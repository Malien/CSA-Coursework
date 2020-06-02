package ua.edu.ukma.csa

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertLeftType
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.network.Message
import ua.edu.ukma.csa.network.MessageType
import ua.edu.ukma.csa.network.Packet
import ua.edu.ukma.csa.network.Packet.Companion.calculateHeaderCRC
import ua.edu.ukma.csa.network.Packet.Companion.calculateMessageCRC
import ua.edu.ukma.csa.network.PacketException
import java.io.ByteArrayInputStream
import java.security.Key
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator

@ExperimentalUnsignedTypes
internal class PacketTest {

    private lateinit var message: Message.Decrypted

    private val key: Key
    private val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")

    init {
        val generator = KeyGenerator.getInstance("AES")
        val random = SecureRandom()
        generator.init(128, random)
        key = generator.generateKey()
    }

    @BeforeEach
    fun setup() {
        message = Message.Decrypted(type = MessageType.OK, userID = 2u, message = "hello".toByteArray())
    }

    @Test
    fun decode() {
        val packet = Packet(clientID = 4u, message = message, packetID = 5u)
        val decoded = Packet.decode<Message.Decrypted>(packet.data)
        assertRight(packet, decoded)
    }

    @Test
    fun decodeEncrypted() {
        val encryptedMessage = message.encrypted(key, cipher)
        val packet = Packet(clientID = 4u, message = encryptedMessage, packetID = 5u)
        val decoded = Packet.decode<Message.Encrypted>(packet.data)
        val decryptedMessage = decoded.map { it.message.decrypted(key, cipher) }
        assertRight(message, decryptedMessage)
    }

    @Test
    fun decode0Size() {
        val data = ByteArray(0)
        assertLeftType<PacketException.Length>(Packet.decode<Message.Decrypted>(data))
    }

    @Test
    fun decodeIncomplete() {
        val packet = Packet(clientID = 4u, message = message, packetID = 5u)
        val decoded = packet.data.copyOfRange(0, packet.size - 2)
        assertLeftType<PacketException.Length>(Packet.decode<Message.Decrypted>(decoded))
    }

    @Test
    fun decodeWrongMessageSize() {
        val packet = Packet(
            clientID = 1u,
            packetID = 2u,
            messageLength = message.size - 1,
            headerCRC = calculateHeaderCRC(0x14, 1u, 2u, messageLength = message.size),
            message = message,
            messageCRC = calculateMessageCRC(message)
        )
        assertLeftType<PacketException>(Packet.decode<Message.Decrypted>(packet.data))
    }

    @Test
    fun decodeWrongMagic() {
        val packet = Packet(
            magic = 0x14,
            clientID = 1u,
            packetID = 2u,
            messageLength = message.size,
            headerCRC = calculateHeaderCRC(0x14, 1u, 2u, messageLength = message.size),
            message = message,
            messageCRC = calculateMessageCRC(message)
        )
        assertLeftType<PacketException.Magic>(Packet.decode<Message.Decrypted>(packet.data))
    }

    @Test
    fun decodeWrongHeaderCRC() {
        val packet = Packet(
            clientID = 1u,
            packetID = 2u,
            messageLength = message.size,
            headerCRC = 0,
            message = message,
            messageCRC = calculateMessageCRC(message)
        )
        assertLeftType<PacketException.CRCCheck>(Packet.decode<Message.Decrypted>(packet.data))
    }

    @Test
    fun decodeWrongMessageCRC() {
        val packet = Packet(
            clientID = 1u,
            packetID = 2u,
            messageLength = message.size,
            headerCRC = calculateHeaderCRC(0x14, 1u, 2u, messageLength = message.size),
            message = message,
            messageCRC = 0
        )
        assertLeftType<PacketException.CRCCheck>(Packet.decode<Message.Decrypted>(packet.data))
    }

    @Test
    fun decodeMultiple() {
        val messages = "Lorem ipsum dolor sit amet".split(' ').asSequence()
        val packets = messages
            .map { Message.Decrypted(type = MessageType.OK, userID = 2u, message = it.toByteArray()) }
            .mapIndexed { idx, message -> Packet(clientID = 3u, message = message, packetID = idx.toULong()) }
            .map { it.data }
            .reduce { acc, data ->
                ByteArray(acc.size + data.size).also {
                    acc.copyInto(it)
                    data.copyInto(it, destinationOffset = acc.size)
                }
            }
        val stream = ByteArrayInputStream(packets)
        val packetSequence = Packet.sequenceFrom<Message.Decrypted>(stream)
        for ((got, expected) in packetSequence.zip(messages)) {
            assertRight(expected, got.map { String(it.message.message) })
        }
    }

    @Test
    fun decodeMultipleEncrypted() {
        val messages = "Lorem ipsum dolor sit amet".split(' ').asSequence()
        val packets = messages
            .map {
                Message.Encrypted(
                    type = MessageType.OK,
                    userID = 2u,
                    message = it.toByteArray(),
                    key = key,
                    cipher = cipher
                )
            }
            .mapIndexed { idx, message -> Packet(clientID = 3u, message = message, packetID = idx.toULong()) }
            .map { it.data }
            .reduce { acc, data ->
                ByteArray(acc.size + data.size).also {
                    acc.copyInto(it)
                    data.copyInto(it, destinationOffset = acc.size)
                }
            }
        val stream = ByteArrayInputStream(packets)
        val packetSequence = Packet.sequenceFrom<Message.Encrypted>(stream)
        for ((got, expected) in packetSequence.zip(messages)) {
            assertRight(expected, got.map { String(it.message.decrypted(key, cipher).message) })
        }
    }

}