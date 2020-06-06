package ua.edu.ukma.csa

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertLeftType
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.model.Product
import ua.edu.ukma.csa.model.ProductID
import ua.edu.ukma.csa.model.SQLiteModel
import ua.edu.ukma.csa.network.FetchException
import ua.edu.ukma.csa.network.UserID
import ua.edu.ukma.csa.network.tcp.TCPClient
import ua.edu.ukma.csa.network.tcp.TCPServer
import ua.edu.ukma.csa.network.tcp.serve
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.concurrent.thread

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TCPPacketTest {

    private val model = SQLiteModel(":memory:")

    private val server = TCPServer(0)
    private val client =
        TCPClient.Decrypted(
            InetSocketAddress(InetAddress.getLocalHost(), server.serverSocket.localPort),
            UserID.assign()
        )
    private lateinit var biscuit: Product

    init {
        thread { server.serve(model) }
    }

    @BeforeEach
    fun populate() {
        model.clear()
        model.addProduct(name = "Biscuit", price = 17.55, count = 10).handleWithThrow()
    }

    @AfterAll
    fun close() {
        server.close()
        client.close()
    }

    @Test
    fun `should add group`() {
        runBlocking {
            val (group) = client.addGroup("name").handleWithThrow()
            client.assignGroup(biscuit.id, group.id)
            assertTrue(group.id in model.getProduct(biscuit.id).handleWithThrow().groups)
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