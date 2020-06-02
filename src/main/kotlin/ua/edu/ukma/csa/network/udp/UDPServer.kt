package ua.edu.ukma.csa.network.udp

import arrow.core.Either
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.network.*
import java.io.Closeable
import java.io.IOException
import java.net.*
import java.security.Key
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import javax.crypto.Cipher

// TODO: Add JavaDocs

@ExperimentalUnsignedTypes
class UDPServer(port: Int, bindAddress: InetAddress = InetAddress.getByName("0.0.0.0")) : Closeable {
    val socket = DatagramSocket(port, bindAddress)

    init {
        socket.soTimeout = TIMEOUT
    }

    @Volatile
    private var shouldStop = false

    class UDPWindow(val packets: Array<UDPPacket?>, var received: UByte = 0u) {
        constructor(window: UByte) : this(arrayOfNulls(window.toInt()))

        operator fun get(idx: UByte) = packets[idx.toInt()]
        operator fun set(idx: UByte, packet: UDPPacket) {
            packets[idx.toInt()] = packet
        }

        val count get() = packets.size.toUByte()
    }

    private val parts = ConcurrentHashMap<SocketAddress, ConcurrentSkipListMap<ULong, UDPWindow>>()

    sealed class Timeout {
        data class User(val address: SocketAddress) : Timeout()
        data class Window(val address: SocketAddress, val packetID: ULong) : Timeout()
    }

    private val timeout = TimeoutHandler<Timeout>()

    data class UDPRequest(val packet: ByteArray, val address: SocketAddress)

    private fun receive() = sequence {
        val buffer = ByteArray(UDPPacket.PACKET_SIZE.toInt())
        val datagram = DatagramPacket(buffer, buffer.size)

        loop@ while (true) {
            try {
                if (shouldStop) break@loop
                socket.receive(datagram)
                val packet = UDPPacket.from(datagram)
                if (packet is Either.Right) {
                    val window = parts[datagram.socketAddress]?.get(packet.b.packetID)

                    timeout.timeout(Timeout.User(datagram.socketAddress), after = USER_TIMEOUT) {
                        it as Timeout.User
                        parts.remove(datagram.socketAddress)
                    }

                    if (window == null) {
                        if (packet.b.window == 1.toUByte()) {
                            yield(UDPRequest(packet.b.chunk, datagram.socketAddress))
                        } else {
                            val userData =
                                parts.getOrPut(datagram.socketAddress) { ConcurrentSkipListMap<ULong, UDPWindow>() }
                            val newBlob = UDPWindow(packet.b.window)
                            if (packet.b.sequenceID > packet.b.window) continue@loop // TODO: send error message back

                            timeout.timeout(
                                Timeout.Window(datagram.socketAddress, packet.b.packetID),
                                after = WINDOW_TIMEOUT
                            ) {
                                it as Timeout.Window
                                parts[it.address]?.remove(it.packetID)
                            }

                            newBlob[packet.b.sequenceID] = packet.b
                            newBlob.received++
                            userData[packet.b.packetID] = newBlob
                        }
                    } else {
                        if (packet.b.window != window.count) continue@loop // TODO: send error message back
                        if (packet.b.sequenceID > packet.b.window) continue@loop // TODO: send error message back
                        if (window[packet.b.sequenceID] == null) {
                            window.received++
                        }
                        window[packet.b.sequenceID] = packet.b
                        if (window.received == packet.b.window) {
                            val combined = ByteArray(window.packets.sumBy { it!!.size.toInt() })
                            window.packets.fold(0) { written, blobPacket ->
                                blobPacket!!.chunk.copyInto(combined, destinationOffset = written)
                                written + blobPacket.chunk.size
                            }
                            parts[datagram.socketAddress]!!.remove(packet.b.packetID)
                            yield(UDPRequest(combined, datagram.socketAddress))
                        }
                    }
                }
            } catch (ignore: SocketTimeoutException) {
            } catch (ignore: IOException) {
            }
        }
    }

    fun serve(handler: (UDPRequest) -> Unit) {
        shouldStop = false
        receive().forEach(handler)
    }

    override fun close() {
        shouldStop = true
        socket.close()
    }

    companion object {
        const val TIMEOUT = 1000
        val USER_TIMEOUT: Duration = Duration.ofMinutes(1)
        val WINDOW_TIMEOUT: Duration = Duration.ofSeconds(30)
    }

}

@ExperimentalUnsignedTypes
fun UDPServer.serve() =
    serve { (data, address) ->
        when (val request = Packet.decode<Message.Decrypted>(data)) {
            is Either.Right -> {
                val response = handlePacket(request.b)
                socket.send(response, address)
            }
            is Either.Left -> {
                val response = Response.Error(request.a.message ?: "").toMessage().handleWithThrow()
                val packet = Packet(clientID = 0, message = response, packetID = request.a.packetID)
                socket.send(packet, address)
            }
        }
    }

/**
 * Will consume the cipher
 */
@ExperimentalUnsignedTypes
fun UDPServer.serve(key: Key, cipher: Cipher) =
    serve { (data, address) ->
        when (val request = Packet.decode<Message.Encrypted>(data)) {
            is Either.Right -> {
                val response = handlePacket(request.b, key, cipher)
                socket.send(response, address)
            }
            is Either.Left -> {
                val response = Response.Error(request.a.message ?: "").toMessage().handleWithThrow()
                val packet =
                    Packet(clientID = 0, message = response, packetID = request.a.packetID)
                socket.send(packet, address)
            }
        }
    }
