package ua.edu.ukma.csa

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.kotlinx.peek
import ua.edu.ukma.csa.model.ProductID
import ua.edu.ukma.csa.model.SQLiteModel
import ua.edu.ukma.csa.network.*
import ua.edu.ukma.csa.network.udp.*
import java.net.*
import java.time.Duration
import kotlin.concurrent.thread
import kotlin.random.nextInt

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExperimentalUnsignedTypes
class UDPServerTest {

    private val model = SQLiteModel(":memory:")
    private val server = UDPServer(0)
    private val userID = UserID.assign()
    private lateinit var socket: DatagramSocket

    init {
        thread { server.serve(model) }
    }

    @AfterAll
    fun close() {
        server.close()
        model.close()
    }

    @BeforeEach
    fun initSocket() {
        socket = DatagramSocket(0)
        socket.soTimeout = 1000
    }

    @Test
    fun `should transfer packet`() {
        val request = Request.GetProduct(ProductID.UNSET).toMessage(userID).handleWithThrow()
        val packet = Packet(clientID = 2u, message = request, packetID = 3u)
        socket.send(packet, InetSocketAddress(InetAddress.getLocalHost(), server.socket.localPort))
        val responseDatagram = DatagramPacket(ByteArray(1024), 1024)
        socket.receive(responseDatagram)
        val (_, _, _, chunk, chunkLength, chunkOffset) = UDPPacket.from(responseDatagram).handleWithThrow()
        val responsePacket = Packet.decode<Message.Decrypted>(chunk, chunkOffset, chunkLength)
        assertRight(MessageType.ERR, responsePacket.map { it.message.type })
    }

    @Test
    fun `should transfer really long packet`() {
        val randomString = generateSequence { kotlin.random.Random.nextInt(32..126) }
            .take(2048)
            .map { it.toChar() }
            .joinToString(separator = "")
        val request = Request.AddGroup(randomString).toMessage(userID).handleWithThrow()
        val packet = Packet(clientID = 2u, message = request, packetID = 4u)
        socket.send(packet, InetSocketAddress(InetAddress.getLocalHost(), server.socket.localPort))
        val responseDatagram = DatagramPacket(ByteArray(1024), 1024)
        socket.receive(responseDatagram)
        val (_, _, _, chunk, chunkLength, chunkOffset) = UDPPacket.from(responseDatagram).handleWithThrow()
        val responsePacket = Packet.decode<Message.Decrypted>(chunk, chunkOffset, chunkLength)
        assertRight(MessageType.OK, responsePacket.map { it.message.type })
    }

    @Test
    fun `message should be received out of order`() {
        val randomString = generateSequence { kotlin.random.Random.nextInt(32..126) }
            .take(2048)
            .map { it.toChar() }
            .joinToString(separator = "")
        val request = Request.AddGroup(randomString).toMessage(userID).handleWithThrow()
        val packet = Packet(clientID = 2u, message = request, packetID = 4u)
        splitData(packet.data, packetID = packet.packetID.toULong())
            .handleWithThrow()
            .shuffled()
            .asSequence()
            .map { it.data }
            .map { DatagramPacket(it, it.size, InetSocketAddress(InetAddress.getLocalHost(), server.socket.localPort)) }
            .forEach(socket::send)
        val responseDatagram = DatagramPacket(ByteArray(1024), 1024)
        socket.receive(responseDatagram)
        val (_, _, _, chunk, chunkLength, chunkOffset) = UDPPacket.from(responseDatagram).handleWithThrow()
        val responsePacket = Packet.decode<Message.Decrypted>(chunk, chunkOffset, chunkLength)
        assertRight(MessageType.OK, responsePacket.map { it.message.type })
    }

    @Test
    fun `should timeout`() {
        val randomString = generateSequence { kotlin.random.Random.nextInt(32..126) }
            .take(2048)
            .map { it.toChar() }
            .joinToString(separator = "")
        val request = Request.AddGroup(randomString).toMessage(userID).handleWithThrow()
        val packet = Packet(clientID = 2u, message = request, packetID = 4u)
        splitData(packet.data, packetID = packet.packetID.toULong())
            .handleWithThrow()
            .asSequence()
            .map { it.data }
            .map { DatagramPacket(it, it.size, InetSocketAddress(InetAddress.getLocalHost(), server.socket.localPort)) }
            .peek { Thread.sleep((UDPServer.WINDOW_TIMEOUT + Duration.ofSeconds(10)).toMillis()) }
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
