package ua.edu.ukma.csa

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertLeftType
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.model.*
import ua.edu.ukma.csa.network.FetchException
import ua.edu.ukma.csa.network.MessageType
import ua.edu.ukma.csa.network.UserID
import ua.edu.ukma.csa.network.tcp.TCPClient
import ua.edu.ukma.csa.network.tcp.TCPServer
import ua.edu.ukma.csa.network.tcp.serve
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.concurrent.thread

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExperimentalUnsignedTypes
class TCPPacketTest {

    private val server = TCPServer(0)
    private val client =
        TCPClient.Decrypted(
            InetSocketAddress(InetAddress.getLocalHost(), server.serverSocket.localPort),
            UserID.assign()
        )
    private val biscuit = Product(id = ProductID.assign(), name = "Biscuit", price = 17.55, count = 10)

    init {
        thread {
            server.serve()
        }
    }

    @BeforeEach
    fun populate() {
        model.clear()
        groups.clear()
        addProduct(biscuit)
    }

    @AfterAll
    fun close() {
        server.close()
        client.close()
    }

    @Test
    fun `should add group`() {
        runBlocking {
            assertRight(MessageType.OK, client.addGroup("name").map { it.type })
            assertRight(MessageType.OK, client.assignGroup(biscuit.id, "name").map { it.type })
            Assertions.assertTrue(groups["name"]!!.contains(biscuit))
        }
    }

    @Test
    fun `should get count`() {
        runBlocking {
            val response = client.getQuantity(biscuit.id)
            assertRight(10, response.map { it.count })
            assertRight(biscuit.id, response.map { it.id })
        }
    }

    @Test
    fun `should receive server error`() {
        runBlocking {
            assertLeftType<FetchException.ServerResponse>(client.getQuantity(ProductID.UNSET))
        }
    }

}