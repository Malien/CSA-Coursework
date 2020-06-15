package ua.edu.ukma.csa.network.tcp

import arrow.core.Either
import arrow.core.Left
import arrow.core.flatMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.protobuf.ProtoBuf
import ua.edu.ukma.csa.kotlinx.arrow.core.unwrap
import ua.edu.ukma.csa.kotlinx.serialization.fload
import ua.edu.ukma.csa.network.*
import ua.edu.ukma.csa.network.udp.UDPClient
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketAddress
import java.security.Key
import java.time.Duration
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import kotlin.concurrent.thread
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

sealed class TCPClient(private val serverAddress: SocketAddress, private val userID: UserID) : Client {
    private val socket = Socket()

    init {
        socket.soTimeout = TIMEOUT
    }

    private val timeout = TimeoutHandler<ULong>()

    private val handlers = ConcurrentSkipListMap<ULong, Handler<Response>>()

    private val packetID = AtomicLong(0)

    @Volatile
    private var operational = false

    @Volatile
    private var shouldStop = false

    private fun handlePacket(maybePacket: Either<PacketException, Packet<Message.Decrypted>>) {
        when (maybePacket) {
            is Either.Right -> {
                val (packet) = maybePacket
                val handler = handlers[packet.packetID] ?: return println("Unhandled packet $packet")
                when (packet.message.type) {
                    MessageType.ERR -> {
                        val response = ProtoBuf.fload(Response.Error.serializer(), packet.message.message)
                            .mapLeft { FetchException.Serialization(it) }
                            .flatMap { Left(FetchException.ServerResponse(it)) }
                        handler.continuation.resume(response)
                    }
                    MessageType.PACKET_BEHIND -> {
                        if (handler.resendBehind) {
                            val id = packetID.incrementAndGet().toULong()
                            val newPacket = handler.packet.copy(packetID = id)
                            handlers[packet.packetID] = handler.copy(packet = newPacket)
                            send(socket.getOutputStream(), newPacket)
                        } else {
                            val response = ProtoBuf.fload(Response.Error.serializer(), packet.message.message)
                                .mapLeft { FetchException.Serialization(it) }
                                .flatMap { Left(FetchException.PacketBehind(packet.packetID)) }
                            handler.continuation.resume(response)
                        }
                    }
                    else -> {
                        val response = ProtoBuf.fload(handler.responseDeserializer, packet.message.message)
                            .mapLeft { FetchException.Serialization(it) }
                        handler.continuation.resume(response)
                    }
                }
            }
            is Either.Left -> {
                val (error) = maybePacket
                if (error.packetID == null) return println("Received invalid packet $error")
                val handler = handlers[error.packetID] ?: return
                if (handler.attempts < handler.retries) retryTimeouts(error.packetID)
                else handler.continuation.resume(Left(FetchException.Parsing(error)))
            }
        }
    }

    abstract fun decode(stream: InputStream): Sequence<Either<PacketException, Packet<Message.Decrypted>>>

    abstract fun send(outputStream: OutputStream, packet: Packet<Message.Decrypted>)

    init {
        thread {
            while (true) {
                if (shouldStop) break
                try {
                    socket.connect(serverAddress)
                    operational = true
                    for (packet in decode(socket.getInputStream())) {
                        handlePacket(packet)
                    }
                } catch (e: IOException) {
                    operational = false
                    Thread.yield()
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <Req : Request, Res : Response> fetch(
        request: Req,
        requestSerializer: SerializationStrategy<Req>,
        responseDeserializer: DeserializationStrategy<Res>,
        resendBehind: Boolean,
        retries: UInt
    ): Fetch<Res> = withContext(Dispatchers.IO) {
        val message = request.toMessage(requestSerializer, userID)
            .mapLeft { FetchException.Serialization(it) }
            .unwrap { return@withContext it }
        val id = packetID.incrementAndGet().toULong()
        val packet = Packet(clientID = UDPClient.CLIENT_ID, message = message, packetID = id)
        return@withContext suspendCoroutine<Fetch<Res>> { continuation ->
            handlers[id] = Handler(
                continuation as Continuation<Fetch<Response>>,
                packet,
                responseDeserializer as DeserializationStrategy<Response>,
                resendBehind,
                retries
            )
            send(socket.getOutputStream(), packet)
            timeout.timeout(id, after = REQUEST_TIMEOUT, handler = ::retryTimeouts)
        }
    }

    private fun retryTimeouts(id: ULong) {
        val handler = handlers[id] ?: return
        if (handler.retries < handler.attempts) {
            if (operational) {
                handlers[id] = handler.copy(attempts = handler.attempts + 1u)
                send(socket.getOutputStream(), handler.packet)
                timeout.timeout(id, after = REQUEST_TIMEOUT, handler = ::retryTimeouts)
            } else {
                timeout.timeout(id, after = REQUEST_TIMEOUT, handler = ::retryTimeouts)
            }
        } else {
            handlers.remove(id)
            handler.continuation.resume(Left(FetchException.Timeout(id, handler.attempts)))
        }
    }

    override fun close() {
        shouldStop = true
        timeout.close()
        socket.close()
    }

    class Decrypted(serverAddress: SocketAddress, userID: UserID) : TCPClient(serverAddress, userID) {
        override fun decode(stream: InputStream): Sequence<Either<PacketException, Packet<Message.Decrypted>>> =
            Packet.sequenceFrom(stream)

        override fun send(outputStream: OutputStream, packet: Packet<Message.Decrypted>) =
            outputStream.write(packet.data)
    }

    class Encrypted(serverAddress: SocketAddress, userID: UserID, private val key: Key, private val cipher: Cipher) :
        TCPClient(serverAddress, userID) {
        override fun decode(stream: InputStream) = Packet.sequenceFrom<Message.Encrypted>(stream)
            .map {
                it.map { packet ->
                    Packet(
                        clientID = packet.clientID,
                        message = packet.message.decrypted(key, cipher),
                        packetID = packet.packetID
                    )
                }
            }

        override fun send(outputStream: OutputStream, packet: Packet<Message.Decrypted>) =
            outputStream.write(
                Packet(
                    packetID = packet.packetID,
                    message = packet.message.encrypted(key, cipher),
                    clientID = packet.clientID
                ).data
            )
    }

    companion object {
        private const val TIMEOUT = 1000
        private val REQUEST_TIMEOUT = Duration.ofSeconds(10)
    }

}