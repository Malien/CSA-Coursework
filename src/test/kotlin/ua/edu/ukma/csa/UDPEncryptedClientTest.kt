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
import ua.edu.ukma.csa.model.*
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

@ExperimentalUnsignedTypes
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UDPEncryptedClientTest {

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
    private val biscuit = Product(id = ProductID.assign(), name = "Biscuit", price = 17.55, count = 10)

    init {
        thread {
            server.serve(key, serverCipher)
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
            val result = client.getQuantity(ProductID.UNSET)
            assertLeftType<FetchException.ServerResponse>(result)
        }
    }

}