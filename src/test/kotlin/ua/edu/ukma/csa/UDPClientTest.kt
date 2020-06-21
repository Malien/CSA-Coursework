package ua.edu.ukma.csa

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ua.edu.ukma.csa.api.serve
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertLeftType
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.model.Product
import ua.edu.ukma.csa.model.ProductID
import ua.edu.ukma.csa.model.SQLiteModel
import ua.edu.ukma.csa.model.UserID
import ua.edu.ukma.csa.network.FetchException
import ua.edu.ukma.csa.network.MessageType
import ua.edu.ukma.csa.network.udp.UDPClient
import ua.edu.ukma.csa.network.udp.UDPServer
import java.net.InetAddress
import kotlin.concurrent.thread
import kotlin.random.nextInt

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UDPClientTest {

    private val model = SQLiteModel(":memory:")

    private val server = UDPServer(0)
    private val client = UDPClient.Decrypted(InetAddress.getLocalHost(), server.socket.localPort, UserID.UNSET)
    private lateinit var biscuit: Product

    init {
        thread { server.serve(model) }
    }

    @BeforeEach
    fun populate() {
        model.clear()
        biscuit = model.addProduct(name = "Biscuit", price = 17.55, count = 10).handleWithThrow()
    }

    @AfterAll
    fun close() {
        server.close()
        client.close()
        model.close()
    }

    @Test
    fun `should add group`() {
        runBlocking {
            val (group) = client.addGroup("name").handleWithThrow()
            client.assignGroup(biscuit.id, group.id)
            val product = model.getProduct(biscuit.id)
            assertRight(true, product.map { group.id in it.groups })
        }
    }

    @Test
    fun `should get product`() {
        runBlocking {
            val response = client.getProduct(biscuit.id)
            assertRight(biscuit, response.map { it.product })
        }
    }

    @Test
    fun `should send large message`() {
        val randomString = generateSequence { kotlin.random.Random.nextInt(32..126) }
            .take(2048)
            .map { it.toChar() }
            .joinToString(separator = "")
        runBlocking {
            val (group) = client.addGroup(randomString).handleWithThrow()
            val res = client.assignGroup(biscuit.id, group.id)
            assertRight(MessageType.OK, res.map { it.type })
            val product = model.getProduct(biscuit.id)
            assertRight(true, product.map { group.id in it.groups })
        }
    }

    @Test
    fun `should receive server error`() {
        runBlocking {
            assertLeftType<FetchException.ServerResponse>(client.getProduct(ProductID.UNSET))
        }
    }

}