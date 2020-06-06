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
import ua.edu.ukma.csa.network.MessageType
import ua.edu.ukma.csa.network.UserID
import ua.edu.ukma.csa.network.udp.UDPClient
import ua.edu.ukma.csa.network.udp.UDPServer
import ua.edu.ukma.csa.network.udp.serve
import java.net.InetAddress
import java.security.Key
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import kotlin.concurrent.thread
import kotlin.random.nextInt

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UDPEncryptedClientTest {

    private val model = SQLiteModel(":memory:")

    private val key: Key
    private val clientCipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    private val serverCipher = Cipher.getInstance("AES/CBC/PKCS5Padding")

    init {
        val generator = KeyGenerator.getInstance("AES")
        val random = SecureRandom()
        generator.init(128, random)
        key = generator.generateKey()
    }

    private val server = UDPServer(0)
    private val client = UDPClient.Encrypted(
        InetAddress.getLocalHost(),
        server.socket.localPort,
        UserID.assign(),
        key,
        clientCipher
    )
    private lateinit var biscuit: Product

    init {
        thread { server.serve(model, key, serverCipher) }
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
    fun `should send large message`() {
        val randomString = generateSequence { kotlin.random.Random.nextInt(32..126) }
            .take(2048)
            .map { it.toChar() }
            .joinToString(separator = "")
        runBlocking {
            val (group) = client.addGroup(randomString).handleWithThrow()
            val res = client.assignGroup(biscuit.id, group.id)
            assertRight(MessageType.OK, res.map { it.type })
            assertTrue(group.id in model.getProduct(biscuit.id).handleWithThrow().groups)
        }
    }

    @Test
    fun `should receive server error`() {
        runBlocking {
            val result = client.getQuantity(ProductID.UNSET)
            assertLeftType<FetchException.ServerResponse>(result)
        }
    }

}