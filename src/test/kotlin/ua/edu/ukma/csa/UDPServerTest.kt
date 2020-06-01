package ua.edu.ukma.csa

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.network.*
import ua.edu.ukma.csa.network.udp.UDPPacket
import ua.edu.ukma.csa.network.udp.UDPServer
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.*
import kotlin.concurrent.thread

@ExperimentalUnsignedTypes
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UDPServerTest {

    private val server: UDPServer = UDPServer(0)
    private var initialized = false

    init {
        thread {
            initialized = true
            server.serve()
        }
    }

    @AfterAll
    fun close() {
        server.close()
    }

    @Test
    fun `should transfer packet`() {
        while (!initialized) Thread.yield()
        val request = Request.GetQuantity(UUID.randomUUID()).toMessage(1).handleWithThrow()
        val packet = Packet(clientID = 2, message = request, packetID = 3)
        val socket = DatagramSocket(0)
        val datagram = DatagramPacket(packet.data, packet.size, InetAddress.getLocalHost(), server.socket.localPort)
        socket.send(datagram)
        val responseDatagram = DatagramPacket(ByteArray(1024), 1024)
        socket.receive(responseDatagram)
        val (_, _, _, chunk, chunkSize, chunkOffset) = UDPPacket.from(responseDatagram).handleWithThrow()
        val responsePacket = Packet.decode<Message.Decrypted>(chunk, chunkOffset, chunkSize)
        assertRight(MessageType.ERR, responsePacket.map { it.message.type })
    }

}
