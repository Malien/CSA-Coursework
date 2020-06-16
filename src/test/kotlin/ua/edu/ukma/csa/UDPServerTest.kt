package ua.edu.ukma.csa

import com.github.snksoft.crc.CRC
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.kotlinx.peek
import ua.edu.ukma.csa.network.udp.UDPPacket
import ua.edu.ukma.csa.network.udp.UDPServer
import ua.edu.ukma.csa.network.udp.sendUDPPackets
import ua.edu.ukma.csa.network.udp.splitData
import java.net.*
import java.nio.ByteBuffer
import java.time.Duration
import kotlin.concurrent.thread
import kotlin.random.Random

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UDPServerTest {

    private val server = UDPServer(0)
    private val socket = DatagramSocket(0)

    init {
        thread { server.serveEcho() }
    }

    @AfterAll
    fun close() {
        server.close()
    }

    @BeforeEach
    fun initSocket() {
        socket.soTimeout = 1000
    }

    @Test
    fun `should transfer packet`() {
        val request = Random.nextBytes(24)
        val crc = CRC.calculateCRC(CRC.Parameters.CRC16, request)
        socket.sendUDPPackets(
            request,
            packetID = 0u,
            to = InetSocketAddress(InetAddress.getLocalHost(), server.socket.localPort)
        )
        val responseDatagram = DatagramPacket(ByteArray(1024), 1024)
        socket.receive(responseDatagram)
        val responseCRC = UDPPacket.from(responseDatagram)
            .map { ByteBuffer.wrap(it.chunk, it.chunkOffset, it.chunkLength) }
            .map { it.long }
        assertRight(crc, responseCRC)
    }

    @Test
    fun `should transfer really long packet`() {
        val request = Random.nextBytes(2048)
        val crc = CRC.calculateCRC(CRC.Parameters.CRC16, request)
        socket.sendUDPPackets(
            request,
            packetID = 0u,
            to = InetSocketAddress(InetAddress.getLocalHost(), server.socket.localPort)
        )
        val responseDatagram = DatagramPacket(ByteArray(1024), 1024)
        socket.receive(responseDatagram)
        val responseCRC = UDPPacket.from(responseDatagram)
            .map { ByteBuffer.wrap(it.chunk, it.chunkOffset, it.chunkLength) }
            .map { it.long }
        assertRight(crc, responseCRC)
    }

    @Test
    fun `message should be received out of order`() {
        val request = Random.nextBytes(2048)
        val crc = CRC.calculateCRC(CRC.Parameters.CRC16, request)
        splitData(request, packetID = 0u)
            .handleWithThrow()
            .toList()
            .shuffled()
            .asSequence()
            .map { it.data }
            .map { DatagramPacket(it, it.size, InetSocketAddress(InetAddress.getLocalHost(), server.socket.localPort)) }
            .forEach(socket::send)
        val responseDatagram = DatagramPacket(ByteArray(1024), 1024)
        socket.receive(responseDatagram)
        val responseCRC = UDPPacket.from(responseDatagram)
            .map { ByteBuffer.wrap(it.chunk, it.chunkOffset, it.chunkLength) }
            .map { it.long }
       assertRight(crc, responseCRC)
    }

    @Test
    fun `should timeout`() {
        val request = Random.nextBytes(2048)
        splitData(request, packetID = 0u)
            .handleWithThrow()
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

    companion object {
        private fun UDPServer.serveEcho() = serve { request ->
            val buffer = ByteBuffer.allocate(8)
            val crc = CRC.calculateCRC(CRC.Parameters.CRC16, request.data)
            buffer.putLong(crc)
            socket.sendUDPPackets(buffer.array(), request.packetCount, request.address)
        }
    }

}
