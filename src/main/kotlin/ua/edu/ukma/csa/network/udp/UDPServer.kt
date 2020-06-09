package ua.edu.ukma.csa.network.udp

import arrow.core.Either
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.model.ModelSource
import ua.edu.ukma.csa.network.*
import java.io.Closeable
import java.io.IOException
import java.net.*
import java.security.Key
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import javax.crypto.Cipher
import kotlin.concurrent.thread

// TODO: Add PING - PONG
// TODO: window size 0 is invalid. Do smt with it

/**
 * Class that encapsulates serving logic through UDP protocol. It will assemble large messages and guarantee that they
 * are formed correctly, even if they do not come in order. It does so by encapsulating larger chunk of data into
 * [UDPPacket] that contains information necessary to assemble larger message. If parts of messages do not arrive in
 * time or got lost during transmission, the whole window will be dropped. If one message from the window contains wrong
 * header data, the whole window will be dropped. The structure of [UDPPacket] is described in that class itself.
 * Be weary that [UDPPacket] does not guarantee message integrity apart from what is available through Datagram itself.
 * To achieve one you would need to provide your own check, like cyclic sum within the packet itself.
 * @see UDPPacket
 * @param port port to which underlying [DatagramSocket] will bind to.
 * @param bindAddress address to which underlying [DatagramSocket] will bind to.
 *                    _Default is `0.0.0.0` (accessible from anywhere)_
 * @throws SocketException if socket cannot bind to specified address or port
 */
class UDPServer(port: Int, bindAddress: InetAddress = InetAddress.getByName("0.0.0.0")) : Closeable {
    val socket = DatagramSocket(port, bindAddress)

    init {
        socket.soTimeout = TIMEOUT
    }

    @Volatile
    private var shouldStop = false

    data class ConnectionState(
        val windows: ConcurrentSkipListMap<ULong, UDPWindow> = ConcurrentSkipListMap(),
        var packetCount: ULong = 0UL
    )

    // TODO: Add buffered packets to save on network traffic a bit.
    //  For e.g. if packet 2 comes before packet 1. Wait a bit and process them in order.
    //  Right now second packet will be processed, and first one will be told to re-send
    private val parts = ConcurrentHashMap<SocketAddress, ConnectionState>()

    sealed class Timeout {
        data class User(val address: SocketAddress) : Timeout()
        data class Window(val address: SocketAddress, val packetID: ULong) : Timeout()
    }

    private val timeout = TimeoutHandler<Timeout>()

    @Suppress("ArrayInDataClass")
    data class UDPRequest(val data: ByteArray, val address: SocketAddress, val packetCount: ULong = 0UL)

    /**
     * Convert all incoming packets into a sequence of [UDPRequest]s. If packet has to be combined with others to form
     * a message, this method will try to assemble them. It may not assemble messages if they contain invalid header,
     * individual sub-packets or are not received on-time. Timeout for forming packets is specified as [WINDOW_TIMEOUT]
     * constant. Additionally this action will cache unfinished user packets for duration of [USER_TIMEOUT]. This will
     * run continuously until either unexpected [DatagramSocket] error is raised, or until [close] method is called.
     * @return sequence of [UDPRequest]s which contain fully formed message and address from where it came from
     * @throws IOException if encountered unknown exception by underlying [DatagramSocket]
     */
    private fun receive() = sequence {
        val buffer = ByteArray(UDPPacket.PACKET_SIZE.toInt())
        val datagram = DatagramPacket(buffer, buffer.size)

        loop@ while (true) {
            try {
                if (shouldStop) break@loop
                socket.receive(datagram)
                val udpPacket = UDPPacket.from(datagram)
                if (udpPacket is Either.Right) {
                    val (packet) = udpPacket
                    val state = parts.getOrPut(datagram.socketAddress) { ConnectionState() }
                    val window = state?.windows?.get(packet.packetID)

                    timeout.timeout(Timeout.User(datagram.socketAddress), after = USER_TIMEOUT) {
                        it as Timeout.User
                        parts.remove(datagram.socketAddress)
                    }

                    if (window == null) {
                        if (packet.sequenceID > packet.window) continue@loop // TODO: send error message back
                        when(packet.window.toInt()) {
                            0 -> continue@loop
                            1 -> {
                                yield(
                                    UDPRequest(
                                        data = packet.chunk,
                                        address = datagram.socketAddress,
                                        packetCount = state.packetCount
                                    )
                                )
                                if (packet.packetID == 0UL) state.packetCount = packet.packetID
                                else state.packetCount = packet.packetID.coerceAtLeast(state.packetCount)
                            }
                            else -> {
                                val newWindow = UDPWindow(packet.window)

                                timeout.timeout(
                                    Timeout.Window(datagram.socketAddress, packet.packetID),
                                    after = WINDOW_TIMEOUT
                                ) {
                                    it as Timeout.Window
                                    parts[it.address]?.windows?.remove(it.packetID)
                                }

                                newWindow[packet.sequenceID] = packet
                                newWindow.received++
                                state.windows[packet.packetID] = newWindow
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
                            state.windows.remove(packet.packetID)
                            yield(
                                UDPRequest(
                                    data = combined,
                                    address = datagram.socketAddress,
                                    packetCount = state.packetCount
                                )
                            )
                            if (packet.packetID == 0UL) state.packetCount = packet.packetID
                            else state.packetCount = packet.packetID.coerceAtLeast(state.packetCount)
                        } else {
                            timeout.timeout(
                                Timeout.Window(datagram.socketAddress, packet.packetID),
                                after = WINDOW_TIMEOUT
                            ) {
                                it as Timeout.Window
                                state.windows.remove(it.packetID)
                            }
                        }
                    }
                }
            } catch (ignore: SocketTimeoutException) {
            } catch (ignore: SocketException) {
            }
        }
    }

    /**
     * Starts serving UDP requests. Will block the thread. Every time compiled packet is received, this method will
     * call handler function on the thread this method was called on. After handler's execution, thread may block again,
     * awaiting further packets
     * @param handler function that is called on every compiled packet
     */
    fun serve(handler: (UDPRequest) -> Unit) {
        shouldStop = false
        receive().forEach(handler)
    }

    /**
     * Tells to seize receive operation. It may not finish exactly after method is called. Closes underlying
     * [DatagramSocket]. Stops timeout collection of invalidated cached user packets, and joins collection thread.
     */
    override fun close() {
        shouldStop = true
        socket.close()
        timeout.close()
    }

    companion object {
        const val TIMEOUT = 1000
        val USER_TIMEOUT: Duration = Duration.ofSeconds(10)
        val WINDOW_TIMEOUT: Duration = Duration.ofSeconds(5)
    }

}

/**
 * Starts serving UDP requests. Will block the thread. On every data chunk received this function will form a
 * corresponding Packet with **decrypted** message within it (aka. [Packet<Message.Decrypted>][Packet]). Every
 * successfully formed packet will be processed by handlePacket function. Every invalid packet will send a
 * [Response.Error] to the sender.
 * @return newly created thread which handles the processing
 */
fun UDPServer.serve(model: ModelSource) = serve { (data, address, packetCount) ->
    thread(name = "UDP-Processing-Thread") {
        socket.send(
            when (val request = Packet.decode<Message.Decrypted>(data)) {
                is Either.Right -> {
                    if (packetCount >= request.b.packetID) {
                        val response = Response.PacketBehind.toMessage().handleWithThrow()
                        Packet(clientID = 0u, message = response, packetID = request.b.packetID)
                    } else model.handlePacket(request.b)
                }
                is Either.Left -> {
                    val response = Response.Error(request.a.message ?: "").toMessage().handleWithThrow()
                    Packet(clientID = 0u, message = response, packetID = request.a.packetID ?: 0UL)
                }
            }, address
        )
    }
}

/**
 * Starts serving UDP requests. Will block the thread. On every data chunk received this function will form a
 * corresponding Packet with **encrypted** message within it (aka. [Packet<Message.Encrypted>][Packet]). Every
 * successfully formed packet will be processed by [handlePacket] function. Every invalid packet will send a
 * [Response.Error] to the sender. **NOTE that this function will consume cipher** to its own use. Most probably this
 * will run in a separate thread and every unintended operation outside of this function will lead to
 * undefined behaviour.
 * @param key key which will be used to decrypt message
 * @param cipher cipher which will be used to decrypt message. Takes ownership of cipher
 * @return newly created thread which handles the processing
 */
fun UDPServer.serve(model: ModelSource, key: Key, cipherFactory: () -> Cipher) = serve { (data, address, packetCount) ->
    thread(name = "UDP-Processing-Thread") {
        val cipher = cipherFactory()
        socket.send(
            when (val request = Packet.decode<Message.Encrypted>(data)) {
                is Either.Right -> {
                    if (packetCount >= request.b.packetID) {
                        val response = Response.PacketBehind.toMessage().handleWithThrow().encrypted(key, cipher)
                        Packet(clientID = 0u, message = response, packetID = request.b.packetID)
                    } else model.handlePacket(request.b, key, cipher)
                }
                is Either.Left -> {
                    val response =
                        Response.Error(request.a.message ?: "").toMessage().handleWithThrow().encrypted(key, cipher)
                    Packet(clientID = 0u, message = response, packetID = request.a.packetID ?: 0UL)
                }
            }, address
        )
    }
}
