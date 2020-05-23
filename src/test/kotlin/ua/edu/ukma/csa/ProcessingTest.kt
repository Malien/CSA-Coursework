package ua.edu.ukma.csa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ua.edu.ukma.csa.kotlinx.nextByte
import ua.edu.ukma.csa.kotlinx.nextInt
import ua.edu.ukma.csa.kotlinx.nextLong
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.network.*
import ua.edu.ukma.csa.network.Packet.Companion.sequenceFrom
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.Key
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator

class ProcessingTest {

    private val random = Random()

    private var packetID = 0L
    private var userID = 0
    private var clientID: Byte = 0

    private val key: Key
    private val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")

    init {
        val generator = KeyGenerator.getInstance("AES")
        val random = SecureRandom()
        generator.init(128, random)
        key = generator.generateKey()
    }

    @BeforeEach
    fun randomize() {
        packetID = random.nextLong(1, Long.MAX_VALUE)
        userID = random.nextInt(1, Int.MAX_VALUE)
        clientID = random.nextByte(1, Byte.MAX_VALUE)
    }

    @Test
    fun validRequest() {
        val message = Message.Decrypted(type = MessageType.ADD_GROUP, userID = userID)
        val packet = Packet(clientID, message, packetID)
        val response = handlePacket(packet)
        assertEquals(MessageType.OK, response.message.type)
    }

    @Test
    fun validEncryptedRequest() {
        val message = Message.Encrypted(type = MessageType.ADD_GROUP, userID = userID, key = key, cipher = cipher)
        val packet = Packet(clientID, message, packetID)
        val response = handlePacket(packet, key, cipher)
        val decrypted = response.message.decrypted(key, cipher)
        assertEquals(MessageType.OK, decrypted.type)
    }

    @Test
    fun invalidMessageType() {
        val message = Message.Decrypted(type = MessageType.ERR, userID = userID)
        val packet = Packet(clientID, message, packetID)
        val response = handlePacket(packet)
        assertEquals(MessageType.ERR, response.message.type)
    }

    @Test
    fun validStreamedRequests() {
        val bytes = generateSequence { Message.Decrypted(MessageType.ADD_GROUP, userID) }
            .mapIndexed { idx, message -> Packet(clientID, message, packetID = idx.toLong()) }
            .map { it.data }
            .take(10) // Arbitrary amount
            .reduce { acc, bytes ->
                ByteArray(acc.size + bytes.size).also {
                    acc.copyInto(it)
                    bytes.copyInto(it, destinationOffset = acc.size)
                }
            }
        val inputStream = ByteArrayInputStream(bytes)
        val outputStream = ByteArrayOutputStream()
        handleStream(inputStream, outputStream)
        val processingStream = ByteArrayInputStream(outputStream.toByteArray())
        for ((idx, packet) in sequenceFrom<Message.Decrypted>(processingStream).withIndex()) {
            assertRight(MessageType.OK, packet.map { it.message.type })
            assertRight(idx, packet.map { it.packetID.toInt() })
        }
    }

    @Test
    fun validEncryptedStreamedRequests() {
        val bytes = generateSequence { Message.Encrypted(MessageType.ADD_GROUP, userID, key = key, cipher = cipher) }
            .mapIndexed { idx, message -> Packet(clientID, message, packetID = idx.toLong()) }
            .map { it.data }
            .take(10) // Arbitrary amount
            .reduce { acc, bytes ->
                ByteArray(acc.size + bytes.size).also {
                    acc.copyInto(it)
                    bytes.copyInto(it, destinationOffset = acc.size)
                }
            }
        val inputStream = ByteArrayInputStream(bytes)
        val outputStream = ByteArrayOutputStream()
        handleStream(inputStream, outputStream, key, cipher)
        val processingStream = ByteArrayInputStream(outputStream.toByteArray())
        for ((idx, packet) in sequenceFrom<Message.Encrypted>(processingStream).withIndex()) {
            assertRight(MessageType.OK, packet.map { it.message.decrypted(key, cipher).type })
            assertRight(idx, packet.map { it.packetID.toInt() })
        }
    }
}