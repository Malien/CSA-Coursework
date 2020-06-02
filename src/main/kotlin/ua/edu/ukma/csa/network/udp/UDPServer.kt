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

// TODO: Add PING - PONG

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
@ExperimentalUnsignedTypes
class UDPServer(port: Int, bindAddress: InetAddress = InetAddress.getByName("0.0.0.0")) : Closeable {
    val socket = DatagramSocket(port, bindAddress)

    init {
        socket.soTimeout = TIMEOUT
    }

    @Volatile
    private var shouldStop = false

    /**
     * Container for managing packets that are in the process of being assembled (windows).
     * @param packets array of packets that are a part of the same window. Size of array is specified by the size of
     *                window itself
     * @param received amount of packets from window that are already received.
     */
    class UDPWindow(val packets: Array<UDPPacket?>, var received: UByte = 0u) {
        /**
         * Constructs an empty window container with the expected size of window
         * @param window the expected size of window
         */
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

    @Suppress("ArrayInDataClass")
    data class UDPRequest(val packet: ByteArray, val address: SocketAddress)

    /**
     * Convert all incoming packets into a sequence of [UDPRequest]s. If packet has to be combined with others to form
     * a message, this method will try to assemble them. It may not assemble messages if they contain invalid header,
     * individual sub-packets or are not received on-time. Timeout for forming packets is specified as [WINDOW_TIMEOUT]
     * constant. Additionally this action will cache unfinished user packets for duration of [USER_TIMEOUT]. This will
     * run continuously until either unexpected [DatagramSocket] error is raised, or until [close] method is called.
     * @return sequence of [UDPRequest]s which contain fully formed message and address from where it came from
     */
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
                        } else {
                            timeout.timeout(
                                Timeout.Window(datagram.socketAddress, packet.b.packetID),
                                after = WINDOW_TIMEOUT
                            ) {
                                it as Timeout.Window
                                parts[it.address]?.remove(it.packetID)
                            }
                        }
                    }
                }
            } catch (ignore: SocketTimeoutException) {
            } catch (ignore: IOException) {
                close()
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
 */
@ExperimentalUnsignedTypes
fun UDPServer.serve() = serve { (data, address) ->
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
 * Starts serving UDP requests. Will block the thread. On every data chunk received this function will form a
 * corresponding Packet with **encrypted** message within it (aka. [Packet<Message.Encrypted>][Packet]). Every
 * successfully formed packet will be processed by [handlePacket] function. Every invalid packet will send a
 * [Response.Error] to the sender. **NOTE that this function will consume cipher** to its own use. Most probably this
 * will run in a separate thread and every unintended operation outside of this function will lead to
 * undefined behaviour.
 * @param key key which will be used to decrypt message
 * @param cipher cipher which will be used to decrypt message. Takes ownership of cipher
 */
@ExperimentalUnsignedTypes
fun UDPServer.serve(key: Key, cipher: Cipher) = serve { (data, address) ->
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
