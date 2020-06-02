package ua.edu.ukma.csa

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.kotlinx.peek
import ua.edu.ukma.csa.network.*
import ua.edu.ukma.csa.network.udp.*
import java.net.*
import java.time.Duration
import java.util.*
import kotlin.concurrent.thread
import kotlin.random.nextInt

@ExperimentalUnsignedTypes
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UDPServerTest {

    private val server = UDPServer(0)
    private var initialized = false
    private val socket = DatagramSocket(0)

    init {
        socket.soTimeout = 1000
        thread {
            initialized = true
            server.serve()
        }
    }

    @AfterAll
    fun close() {
        server.close()
    }

    @BeforeAll
    fun waitInitialization() {
        while (!initialized) Thread.yield()
    }

    @Test
    fun `should transfer packet`() {
        val request = Request.GetQuantity(UUID.randomUUID()).toMessage(1).handleWithThrow()
        val packet = Packet(clientID = 2, message = request, packetID = 3)
        socket.send(packet, InetSocketAddress(InetAddress.getLocalHost(), server.socket.localPort))
        val responseDatagram = DatagramPacket(ByteArray(1024), 1024)
        socket.receive(responseDatagram)
        val (_, _, _, chunk, chunkSize, chunkOffset) = UDPPacket.from(responseDatagram).handleWithThrow()
        val responsePacket = Packet.decode<Message.Decrypted>(chunk, chunkOffset, chunkSize)
        assertRight(MessageType.ERR, responsePacket.map { it.message.type })
    }

    @Test
    fun `should transfer really long packet`() {
        val randomString = generateSequence { kotlin.random.Random.nextInt(32..126) }
            .take(2048)
            .map { it.toChar() }
            .joinToString(separator = "")
        val request = Request.AddGroup(randomString).toMessage(1).handleWithThrow()
        val packet = Packet(clientID = 2, message = request, packetID = 4)
        socket.send(packet, InetSocketAddress(InetAddress.getLocalHost(), server.socket.localPort))
        val responseDatagram = DatagramPacket(ByteArray(1024), 1024)
        socket.receive(responseDatagram)
        val (_, _, _, chunk, chunkSize, chunkOffset) = UDPPacket.from(responseDatagram).handleWithThrow()
        val responsePacket = Packet.decode<Message.Decrypted>(chunk, chunkOffset, chunkSize)
        assertRight(MessageType.OK, responsePacket.map { it.message.type })
    }

    @Test
    fun `message should be received out of order`() {
        val randomString = generateSequence { kotlin.random.Random.nextInt(32..126) }
            .take(2048)
            .map { it.toChar() }
            .joinToString(separator = "")
        val request = Request.AddGroup(randomString).toMessage(1).handleWithThrow()
        val packet = Packet(clientID = 2, message = request, packetID = 4)
        splitData(packet.data, packetID = packet.packetID.toULong())
            .handleWithThrow()
            .shuffled()
            .asSequence()
            .map { it.data }
            .map { DatagramPacket(it, it.size, InetSocketAddress(InetAddress.getLocalHost(), server.socket.localPort)) }
            .forEach(socket::send)
        val responseDatagram = DatagramPacket(ByteArray(1024), 1024)
        socket.receive(responseDatagram)
        val (_, _, _, chunk, chunkSize, chunkOffset) = UDPPacket.from(responseDatagram).handleWithThrow()
        val responsePacket = Packet.decode<Message.Decrypted>(chunk, chunkOffset, chunkSize)
        assertRight(MessageType.OK, responsePacket.map { it.message.type })
    }

    @Test
    fun `should timeout`() {
        val randomString = generateSequence { kotlin.random.Random.nextInt(32..126) }
            .take(2048)
            .map { it.toChar() }
            .joinToString(separator = "")
        val request = Request.AddGroup(randomString).toMessage(1).handleWithThrow()
        val packet = Packet(clientID = 2, message = request, packetID = 4)
        splitData(packet.data, packetID = packet.packetID.toULong())
            .handleWithThrow()
            .asSequence()
            .map { it.data }
            .map { DatagramPacket(it, it.size, InetSocketAddress(InetAddress.getLocalHost(), server.socket.localPort)) }
            .peek { Thread.sleep((UDPServer.WINDOW_TIMEOUT + Duration.ofSeconds(5)).toMillis()) }
            .forEach(socket::send)
        val responseDatagram = DatagramPacket(ByteArray(1024), 1024)
        var timedout = false
        try {
            socket.receive(responseDatagram)
        } catch (ignored: SocketTimeoutException) {
            timedout = true
        }
        assertTrue(timedout)
    }

}
