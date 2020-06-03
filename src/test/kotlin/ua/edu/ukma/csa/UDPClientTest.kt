package ua.edu.ukma.csa

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ua.edu.ukma.csa.kotlinx.arrow.core.then
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertLeftType
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.model.Product
import ua.edu.ukma.csa.model.addProduct
import ua.edu.ukma.csa.model.groups
import ua.edu.ukma.csa.model.model
import ua.edu.ukma.csa.network.FetchError
import ua.edu.ukma.csa.network.MessageType
import ua.edu.ukma.csa.network.udp.UDPClient
import ua.edu.ukma.csa.network.udp.UDPServer
import ua.edu.ukma.csa.network.udp.serve
import java.net.InetAddress
import java.util.*
import kotlin.concurrent.thread
import kotlin.random.nextInt

@ExperimentalUnsignedTypes
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UDPClientTest {

    private val server = UDPServer(0)
    private val client = UDPClient.Decrypted(InetAddress.getLocalHost(), server.socket.localPort, 3u)
    private val biscuit = Product(name = "Biscuit", price = 17.55, count = 10)

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
            assertTrue(groups["name"]!!.contains(biscuit))
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
    fun `should send large message`() {
        val randomString = generateSequence { kotlin.random.Random.nextInt(32..126) }
            .take(2048)
            .map { it.toChar() }
            .joinToString(separator = "")
        runBlocking {
            val res = client.addGroup(randomString)
                .then { client.assignGroup(biscuit.id, randomString) }
            assertRight(MessageType.OK, res.map { it.type })
            assertTrue(groups[randomString]!!.contains(biscuit))
        }
    }

    @Test
    fun `should receive server error`() {
        runBlocking {
            assertLeftType<FetchError.ServerResponse>(client.getQuantity(UUID.randomUUID()))
        }
    }

}