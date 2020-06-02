package ua.edu.ukma.csa.network.udp

import arrow.core.Either
import arrow.core.Left
import arrow.core.flatMap
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.protobuf.ProtoBuf
import ua.edu.ukma.csa.kotlinx.serialization.fload
import ua.edu.ukma.csa.network.*
import java.io.IOException
import java.net.*
import java.security.Key
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import kotlin.concurrent.thread
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

// TODO: Add timeouts

sealed class UDPClient(protected val serverAddress: SocketAddress) : Client {
    protected val socket = DatagramSocket(0)

    data class Handler<R : Response>(
        val continuation: Continuation<Either<NetworkError, Response>>,
        val packet: Packet<Message.Decrypted>,
        val responseDeserializer: DeserializationStrategy<R>,
        val resendBehind: Boolean,
        val retries: UInt,
        val attempts: UInt = 0u
    )

    private val handlers = ConcurrentHashMap<ULong, Handler<Response>>()

    private val parts = ConcurrentSkipListMap<ULong, UDPWindow>()

    private var packetID = AtomicLong(0L)

    override val clientID = CLIENT_ID

    abstract fun parsePacket(
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size
    ): Either<PacketException, Packet<Message.Decrypted>>

    abstract fun send(packet: Packet<Message.Decrypted>): Either<RuntimeException, Unit>

    private fun dispatchPacket(maybePacket: Either<PacketException, Packet<Message.Decrypted>>) {
        maybePacket.map { packet ->
            val handler = handlers[packet.packetID] ?: return@map println("Unhandled packet $packet")
            when (packet.message.type) {
                MessageType.ERR -> {
                    val response = ProtoBuf.fload(Response.Error.serializer(), packet.message.message)
                        .mapLeft { NetworkError.Serialization(it) }
                        .flatMap { Left(NetworkError.ServerResponse(it)) }
                    handler.continuation.resume(response)
                }
                MessageType.PACKET_BEHIND -> {
                    if (handler.resendBehind) {
                        val id = packetID.incrementAndGet().toULong()
                        val newPacket = handler.packet.copy(packetID = id)
                        handlers[packet.packetID] = handler.copy(packet = newPacket)
                        send(newPacket)
                    } else {
                        val response = ProtoBuf.fload(Response.Error.serializer(), packet.message.message)
                            .mapLeft { NetworkError.Serialization(it) }
                            .flatMap { Left(NetworkError.PacketBehind(packet.packetID)) }
                        handler.continuation.resume(response)
                    }
                }
                else -> {
                    val response = ProtoBuf.fload(handler.responseDeserializer, packet.message.message)
                        .mapLeft { NetworkError.Serialization(it) }
                    handler.continuation.resume(response)
                }
            }
        }
    }

    @Volatile
    var shouldStop = false

    private val networkThread = thread(name = "UDP-Client-Thread") {
        val buffer = ByteArray(UDPPacket.PACKET_SIZE.toInt())
        val datagram = DatagramPacket(buffer, buffer.size)
        loop@ while (true) {
            try {
                if (shouldStop) break@loop
                socket.receive(datagram)
                if (datagram.socketAddress != serverAddress) continue@loop
                val udpPacket = UDPPacket.from(datagram)
                if (udpPacket is Either.Right) {
                    val (packet) = udpPacket
                    val window = parts[packet.packetID]

                    if (window == null) {
                        if (packet.window <= packet.sequenceID) continue@loop
                        when (packet.window.toInt()) {
                            0 -> continue@loop
                            1 -> dispatchPacket(
                                parsePacket(packet.chunk, packet.chunkOffset, packet.chunkLength)
                            )
                            else -> {
                                val newWindow = UDPWindow(packet.window)
                                newWindow[packet.sequenceID] = packet
                                newWindow.received++
                                parts[packet.packetID] = newWindow
                            }
                        }
                    } else {
                        if (packet.window != window.count) continue@loop // TODO: send error message back
                        if (packet.sequenceID > packet.window) continue@loop // TODO: send error message back
                        if (window[packet.sequenceID] == null) {
                            window.received++
                        }
                        window[packet.sequenceID] = packet
                        if (window.received == packet.window) {
                            val combined = ByteArray(window.packets.sumBy { it!!.size.toInt() })
                            window.packets.fold(0) { written, blobPacket ->
                                blobPacket!!.chunk.copyInto(combined, destinationOffset = written)
                                written + blobPacket.chunk.size
                            }
                            parts.remove(packet.packetID)
                            dispatchPacket(parsePacket(combined))
                        }
                    }
                }
            } catch (ignore: SocketTimeoutException) {
            } catch (e: IOException) {
                shouldStop = true
                println(e)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <R : Response> request(
        message: Message.Decrypted,
        responseDeserializer: DeserializationStrategy<R>,
        resendBehind: Boolean,
        retries: UInt
    ): Either<NetworkError, R> = suspendCoroutine { continuation ->
        val id = packetID.incrementAndGet().toULong()
        val packet = Packet(clientID = CLIENT_ID, message = message, packetID = id)
        handlers[id] = Handler(
            continuation as Continuation<Either<NetworkError, Response>>,
            packet,
            responseDeserializer as DeserializationStrategy<Response>,
            resendBehind,
            retries
        )
        send(packet)
    }

    override fun close() {
        shouldStop = true
        networkThread.join()
        socket.close()
    }

    class Decrypted(serverAddress: SocketAddress) : UDPClient(serverAddress) {
        constructor(address: InetAddress, port: Int) : this(InetSocketAddress(address, port))

        override fun parsePacket(
            data: ByteArray,
            offset: Int,
            length: Int
        ): Either<PacketException, Packet<Message.Decrypted>> = Packet.decode(data, offset, length)

        override fun send(packet: Packet<Message.Decrypted>) = socket.send(packet, to = serverAddress)
    }

    class Encrypted(serverAddress: SocketAddress, private val key: Key, private val cipher: Cipher) :
        UDPClient(serverAddress) {
        constructor(address: InetAddress, port: Int, key: Key, cipher: Cipher) : this(
            InetSocketAddress(address, port),
            key,
            cipher
        )

        override fun parsePacket(
            data: ByteArray,
            offset: Int,
            length: Int
        ): Either<PacketException, Packet<Message.Decrypted>> =
            Packet.decode<Message.Encrypted>(data, offset, length).map {
                Packet(clientID = it.clientID, message = it.message.decrypted(key, cipher), packetID = it.packetID)
            }

        override fun send(packet: Packet<Message.Decrypted>) =
            socket.send(
                Packet(
                    clientID = packet.clientID,
                    packetID = packet.packetID,
                    message = packet.message.encrypted(key, cipher)
                ), to = serverAddress
            )
    }

    companion object {
        const val CLIENT_ID: UByte = 1u
    }
}

