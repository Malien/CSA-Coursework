package ua.edu.ukma.csa

import arrow.core.Left
import arrow.core.Right
import arrow.core.flatMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.kotlinx.nextByte
import ua.edu.ukma.csa.kotlinx.nextInt
import ua.edu.ukma.csa.kotlinx.nextLong
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.kotlinx.serialization.functionalParse
import ua.edu.ukma.csa.model.*
import ua.edu.ukma.csa.network.*
import ua.edu.ukma.csa.network.Packet.Companion.sequenceFrom
import ua.edu.ukma.csa.network.Request.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.Key
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import kotlin.concurrent.thread

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

    private lateinit var biscuit: Product
    private lateinit var conditioner: Product
    private lateinit var iceCream: Product

    @BeforeEach
    fun populate() {
        model.clear()
        groups.clear()

        biscuit = Product(name = "Biscuit", count = 100, price = 20.5)
        conditioner = Product(name = "Hair conditioner", count = 20, price = 13.75)
        iceCream = Product(name = "Vanilla Ice Cream", count = 50, price = 7.59)

        addProduct(biscuit)
        addProduct(conditioner)
        addProduct(iceCream)

        addGroup("Sweets")
        addGroup("Cosmetics")
        addGroup("Diary")

        assignGroup(biscuit.id, "Sweets")
        assignGroup(conditioner.id, "Cosmetics")
        assignGroup(iceCream.id, "Diary")
    }

    private val json = Json(JsonConfiguration.Stable)

    @Test
    fun validRequest() {
        val request = GetQuantity(biscuit.id)
        val response = request.toMessage(userID, GetQuantity.serializer())
            .map { message -> Packet(clientID, message, packetID) }
            .map(::handlePacket)
            .flatMap { packet ->
                if (packet.message.type == MessageType.OK) Right(packet.message)
                else Left(assertEquals(MessageType.OK, packet.message.type))
            }
            .flatMap { json.functionalParse(Response.Quantity.serializer(), String(it.message)) }
        assertRight(Response.Quantity(biscuit.id, biscuit.count), response)
    }

    @Test
    fun validEncryptedRequest() {
        val request = GetQuantity(biscuit.id)
        val response = request.toMessage(userID, GetQuantity.serializer())
            .map { message -> message.encrypted(key, cipher) }
            .map { encrypted -> Packet(clientID, encrypted, packetID) }
            .map { packet -> handlePacket(packet, key, cipher) }
            .flatMap { packet ->
                if (packet.message.type == MessageType.OK) Right(packet.message)
                else Left(assertEquals(MessageType.OK, packet.message.type))
            }
            .map { encryptedMessage -> encryptedMessage.decrypted(key, cipher) }
            .flatMap { json.functionalParse(Response.Quantity.serializer(), String(it.message)) }
        assertRight(Response.Quantity(biscuit.id, biscuit.count), response)
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
        val bytes = generateSequence { GetQuantity(biscuit.id) }
            .map { it.toMessage(userID, GetQuantity.serializer()).handleWithThrow() }
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
            val response = packet.map {
                assertEquals(idx, it.packetID.toInt())
                it.message
            }.flatMap {
                json.functionalParse(Response.Quantity.serializer(), String(it.message))
            }
            assertRight(Response.Quantity(biscuit.id, biscuit.count), response)
        }
    }

    @Test
    fun validEncryptedStreamedRequests() {
        val bytes = generateSequence { GetQuantity(biscuit.id) }
            .map { it.toMessage(userID, GetQuantity.serializer()).handleWithThrow() }
            .map { it.encrypted(key, cipher) }
            .mapIndexed { idx, message -> Packet(clientID, message, idx.toLong()) }
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
            val response = packet.map {
                assertEquals(idx, it.packetID.toInt())
                it.message.decrypted(key, cipher)
            }.flatMap {
                json.functionalParse(Response.Quantity.serializer(), String(it.message))
            }
            assertRight(Response.Quantity(biscuit.id, biscuit.count), response)
        }
    }

    @RepeatedTest(20)
    fun parallelChanges() {
        val requestsList = listOf(
            sequenceOf(
                AddGroup("Special").toMessage(userID, AddGroup.serializer()),
                AssignGroup(biscuit.id, "Special").toMessage(userID, AssignGroup.serializer()),
                AssignGroup(iceCream.id, "Special").toMessage(userID, AssignGroup.serializer())
            ), sequenceOf(
                AddGroup("Discounted").toMessage(userID, AddGroup.serializer()),

                SetPrice(biscuit.id, 17.5).toMessage(userID, SetPrice.serializer()),
                AssignGroup(biscuit.id, "Discounted").toMessage(userID, AssignGroup.serializer()),

                AssignGroup(conditioner.id, "Discounted").toMessage(userID, AssignGroup.serializer()),
                SetPrice(conditioner.id, 9.70).toMessage(userID, SetPrice.serializer()),
                Exclude(conditioner.id, 10).toMessage(userID, Exclude.serializer())
            ), sequenceOf(
                Exclude(biscuit.id, 20).toMessage(userID, Exclude.serializer())
            ), sequenceOf(
                Exclude(biscuit.id, 10).toMessage(userID, Exclude.serializer()),
                Exclude(iceCream.id, 10).toMessage(userID, Exclude.serializer())
            ), sequenceOf(
                Exclude(biscuit.id, 10).toMessage(userID, Exclude.serializer()),
                Exclude(iceCream.id, 10).toMessage(userID, Exclude.serializer())
            ), sequenceOf(
                Include(biscuit.id, 60).toMessage(userID, Include.serializer()),
                Include(conditioner.id, 20).toMessage(userID, Include.serializer()),
                Include(iceCream.id, 5).toMessage(userID, Include.serializer())
            )
        )

        requestsList
            .map { request ->
                thread {
                    val localCipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    val bytes = request
                        .map { it.handleWithThrow() }
                        .map { it.encrypted(key, localCipher) }
                        .mapIndexed { idx, message -> Packet(clientID, message, idx.toLong()) }
                        .map { it.data }
                        .reduce { acc, bytes ->
                            ByteArray(acc.size + bytes.size).also {
                                acc.copyInto(it)
                                bytes.copyInto(it, destinationOffset = acc.size)
                            }
                        }
                    val stream = ByteArrayInputStream(bytes)
                    val out = ByteArrayOutputStream()
                    handleStream(stream, out, key, localCipher)
                }
            }
            .forEach { it.join() }

        Assertions.assertTrue(groups.containsKey("Special"))
        Assertions.assertTrue(groups.containsKey("Discounted"))
        Assertions.assertTrue(biscuit in groups["Special"]!!)
        Assertions.assertTrue(iceCream in groups["Special"]!!)
        Assertions.assertTrue(biscuit in groups["Discounted"]!!)
        Assertions.assertTrue(conditioner in groups["Discounted"]!!)
        assertEquals(17.5, biscuit.price)
        assertEquals(9.70, conditioner.price)
        assertRight(120, getQuantity(biscuit.id))
        assertRight(30, getQuantity(conditioner.id))
        assertRight(35, getQuantity(iceCream.id))

        fun requestQuantity(id: UUID) =
            GetQuantity(id).toMessage(userID, GetQuantity.serializer())
                .map { message -> message.encrypted(key, cipher) }
                .map { encrypted -> Packet(clientID, encrypted, packetID) }
                .map { packet -> handlePacket(packet, key, cipher) }
                .flatMap { packet ->
                    if (packet.message.type == MessageType.OK) Right(packet.message)
                    else Left(assertEquals(MessageType.OK, packet.message.type))
                }
                .map { encrypted -> encrypted.decrypted(key, cipher) }
                .flatMap { message -> json.functionalParse(Response.Quantity.serializer(), String(message.message)) }
                .map { it.count }

        assertRight(120, requestQuantity(biscuit.id))
        assertRight(30, requestQuantity(conditioner.id))
        assertRight(35, requestQuantity(iceCream.id))
    }
}