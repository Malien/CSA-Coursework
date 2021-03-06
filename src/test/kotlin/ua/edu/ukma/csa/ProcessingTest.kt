package ua.edu.ukma.csa

import arrow.core.Left
import arrow.core.Right
import arrow.core.flatMap
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ua.edu.ukma.csa.api.handlePacket
import ua.edu.ukma.csa.api.handleStream
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.kotlinx.nextByte
import ua.edu.ukma.csa.kotlinx.nextLong
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.kotlinx.serialization.fload
import ua.edu.ukma.csa.model.Group
import ua.edu.ukma.csa.model.Product
import ua.edu.ukma.csa.model.SQLiteModel
import ua.edu.ukma.csa.model.UserID
import ua.edu.ukma.csa.network.*
import ua.edu.ukma.csa.network.Packet.Companion.sequenceFrom
import ua.edu.ukma.csa.network.Request.GetProduct
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.Key
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessingTest {

    private val model = SQLiteModel(":memory:")

    private lateinit var sweets: Group
    private lateinit var cosmetics: Group
    private lateinit var diary: Group

    private val random = Random()

    private var packetID = 0uL
    private var userID = UserID.UNSET
    private var clientID: UByte = 0u

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
        packetID = random.nextLong(1, Long.MAX_VALUE).toULong()
        clientID = random.nextByte(1, Byte.MAX_VALUE).toUByte()
    }

    private lateinit var biscuit: Product
    private lateinit var conditioner: Product
    private lateinit var iceCream: Product

    @BeforeEach
    fun populate() {
        model.clear()

        biscuit = model.addProduct(name = "Biscuit", count = 100, price = 20.5).handleWithThrow()
        conditioner = model.addProduct(name = "Hair conditioner", count = 20, price = 13.75).handleWithThrow()
        iceCream = model.addProduct(name = "Vanilla Ice Cream", count = 50, price = 7.59).handleWithThrow()

        sweets = model.addGroup("Sweets").handleWithThrow()
        cosmetics = model.addGroup("Cosmetics").handleWithThrow()
        diary = model.addGroup("Diary").handleWithThrow()

        model.assignGroup(biscuit.id, sweets.id)
        model.assignGroup(conditioner.id, cosmetics.id)
        model.assignGroup(iceCream.id, diary.id)
    }

    @AfterAll
    fun close() {
        model.close()
    }

    @Test
    fun validRequest() {
        val request = GetProduct(biscuit.id)
        val response = request.toMessage(userID)
            .map { message -> Packet(clientID, message, packetID) }
            .map(model::handlePacket)
            .flatMap { packet ->
                if (packet.message.type == MessageType.OK) Right(packet.message)
                else Left(assertEquals(MessageType.OK, packet.message.type))
            }
            .flatMap { ProtoBuf.fload(Response.Product.serializer(), it.message) }
        assertRight(Response.Product(biscuit), response)
    }

    @Test
    fun validEncryptedRequest() {
        val request = GetProduct(biscuit.id)
        val response = request.toMessage(userID)
            .map { message -> message.encrypted(key, cipher) }
            .map { encrypted -> Packet(clientID, encrypted, packetID) }
            .map { packet -> model.handlePacket(packet, key, cipher) }
            .flatMap { packet ->
                if (packet.message.type == MessageType.OK) Right(packet.message)
                else Left(assertEquals(MessageType.OK, packet.message.type))
            }
            .map { encryptedMessage -> encryptedMessage.decrypted(key, cipher) }
            .flatMap { ProtoBuf.fload(Response.Product.serializer(), it.message) }
        assertRight(Response.Product(biscuit), response)
    }

    @Test
    fun invalidMessageType() {
        val message = Message.Decrypted(type = MessageType.ERR, userID = userID)
        val packet = Packet(clientID, message, packetID)
        val response = model.handlePacket(packet)
        assertEquals(MessageType.ERR, response.message.type)
    }

    @Test
    fun validStreamedRequests() {
        val bytes = generateSequence { GetProduct(biscuit.id) }
            .map { it.toMessage(userID).handleWithThrow() }
            .mapIndexed { idx, message -> Packet(clientID, message, packetID = idx.toULong()) }
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
        model.handleStream(inputStream, outputStream)
        val processingStream = ByteArrayInputStream(outputStream.toByteArray())
        for ((idx, packet) in sequenceFrom<Message.Decrypted>(processingStream).withIndex()) {
            val response = packet.map {
                assertEquals(idx, it.packetID.toInt())
                it.message
            }.flatMap {
                ProtoBuf.fload(Response.Product.serializer(), it.message)
            }
            assertRight(Response.Product(biscuit), response)
        }
    }

    @Test
    fun validEncryptedStreamedRequests() {
        val bytes = generateSequence { GetProduct(biscuit.id) }
            .map { it.toMessage(userID).handleWithThrow() }
            .map { it.encrypted(key, cipher) }
            .mapIndexed { idx, message -> Packet(clientID, message, idx.toULong()) }
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
        model.handleStream(inputStream, outputStream, key, cipher)
        val processingStream = ByteArrayInputStream(outputStream.toByteArray())
        for ((idx, packet) in sequenceFrom<Message.Encrypted>(processingStream).withIndex()) {
            val response = packet.map {
                assertEquals(idx, it.packetID.toInt())
                it.message.decrypted(key, cipher)
            }.flatMap {
                ProtoBuf.fload(Response.Product.serializer(), it.message)
            }
            assertRight(Response.Product(biscuit), response)
        }
    }

//    @RepeatedTest(20)
//    fun parallelChanges() {
//        val requestsList = listOf(
//            Either.fx {
//                AddGroup("Special").toMessage(userID),
//                AssignGroup(biscuit.id, "Special").toMessage(userID),
//                AssignGroup(iceCream.id, "Special").toMessage(userID)
//            }
//            ,sequenceOf(
//                AddGroup("Discounted").toMessage(userID),
//
//                SetPrice(biscuit.id, 17.5).toMessage(userID),
//                AssignGroup(biscuit.id, "Discounted").toMessage(userID),
//
//                AssignGroup(conditioner.id, "Discounted").toMessage(userID),
//                SetPrice(conditioner.id, 9.70).toMessage(userID),
//                Exclude(conditioner.id, 10).toMessage(userID)
//            ), sequenceOf(
//                Exclude(biscuit.id, 20).toMessage(userID)
//            ), sequenceOf(
//                Exclude(biscuit.id, 10).toMessage(userID),
//                Exclude(iceCream.id, 10).toMessage(userID)
//            ), sequenceOf(
//                Exclude(biscuit.id, 10).toMessage(userID),
//                Exclude(iceCream.id, 10).toMessage(userID)
//            ), sequenceOf(
//                Include(biscuit.id, 60).toMessage(userID),
//                Include(conditioner.id, 20).toMessage(userID),
//                Include(iceCream.id, 5).toMessage(userID)
//            )
//        )
//
//        requestsList
//            .map { request ->
//                thread {
//                    val localCipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
//                    val bytes = request
//                        .map { it.handleWithThrow() }
//                        .map { it.encrypted(key, localCipher) }
//                        .mapIndexed { idx, message -> Packet(clientID, message, idx.toULong()) }
//                        .map { it.data }
//                        .reduce { acc, bytes ->
//                            ByteArray(acc.size + bytes.size).also {
//                                acc.copyInto(it)
//                                bytes.copyInto(it, destinationOffset = acc.size)
//                            }
//                        }
//                    val stream = ByteArrayInputStream(bytes)
//                    val out = ByteArrayOutputStream()
//                    handleStream(stream, out, key, localCipher)
//                }
//            }
//            .forEach { it.join() }
//
//        Assertions.assertTrue(groups.containsKey("Special"))
//        Assertions.assertTrue(groups.containsKey("Discounted"))
//        Assertions.assertTrue(biscuit in groups["Special"]!!)
//        Assertions.assertTrue(iceCream in groups["Special"]!!)
//        Assertions.assertTrue(biscuit in groups["Discounted"]!!)
//        Assertions.assertTrue(conditioner in groups["Discounted"]!!)
//        assertEquals(17.5, biscuit.price)
//        assertEquals(9.70, conditioner.price)
//        assertRight(120, getQuantity(biscuit.id))
//        assertRight(30, getQuantity(conditioner.id))
//        assertRight(35, getQuantity(iceCream.id))
//
//        fun requestQuantity(id: ProductID) =
//            GetQuantity(id).toMessage(userID)
//                .map { message -> message.encrypted(key, cipher) }
//                .map { encrypted -> Packet(clientID, encrypted, packetID) }
//                .map { packet -> handlePacket(packet, key, cipher) }
//                .flatMap { packet ->
//                    if (packet.message.type == MessageType.OK) Right(packet.message)
//                    else Left(assertEquals(MessageType.OK, packet.message.type))
//                }
//                .map { encrypted -> encrypted.decrypted(key, cipher) }
//                .flatMap { message -> ProtoBuf.fload(Response.Quantity.serializer(), message.message) }
//                .map { it.count }
//
//        assertRight(120, requestQuantity(biscuit.id))
//        assertRight(30, requestQuantity(conditioner.id))
//        assertRight(35, requestQuantity(iceCream.id))
//    }
}