package ua.edu.ukma.csa

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ua.edu.ukma.csa.Packet.Companion.calculateHeaderCRC
import ua.edu.ukma.csa.Packet.Companion.calculateMessageCRC
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertLeftType
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import java.io.ByteArrayInputStream
import java.security.Key
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator

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
        message = Message.Decrypted(type = 1, userID = 2, message = "hello".toByteArray())
    }

    @Test
    fun decode() {
        val packet = Packet(clientID = 4, message = message, packetID = 5)
        val decoded = Packet.decode<Message.Decrypted>(packet.data)
        assertRight(packet, decoded)
    }

    @Test
    fun decodeEncrypted() {
        val encryptedMessage = message.encrypted(key, cipher)
        val packet = Packet(clientID = 4, message = encryptedMessage, packetID = 5)
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
        val packet = Packet(clientID = 4, message = message, packetID = 5)
        val decoded = packet.data.copyOfRange(0, packet.size - 2)
        assertLeftType<PacketException.Length>(Packet.decode<Message.Decrypted>(decoded))
    }

    @Test
    fun decodeWrongMessageSize() {
        val packet = Packet(
            clientID = 1,
            packetID = 2,
            messageLength = message.size - 1,
            headerCRC = calculateHeaderCRC(0x14, 1, 2, messageLength = message.size),
            message = message,
            messageCRC = calculateMessageCRC(message)
        )
        assertLeftType<PacketException>(Packet.decode<Message.Decrypted>(packet.data))
    }

    @Test
    fun decodeWrongMagic() {
        val packet = Packet(
            magic = 0x14,
            clientID = 1,
            packetID = 2,
            messageLength = message.size,
            headerCRC = calculateHeaderCRC(0x14, 1, 2, messageLength = message.size),
            message = message,
            messageCRC = calculateMessageCRC(message)
        )
        assertLeftType<PacketException.Magic>(Packet.decode<Message.Decrypted>(packet.data))
    }

    @Test
    fun decodeWrongHeaderCRC() {
        val packet = Packet(
            clientID = 1,
            packetID = 2,
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
            clientID = 1,
            packetID = 2,
            messageLength = message.size,
            headerCRC = calculateHeaderCRC(0x14, 1, 2, messageLength = message.size),
            message = message,
            messageCRC = 0
        )
        assertLeftType<PacketException.CRCCheck>(Packet.decode<Message.Decrypted>(packet.data))
    }

    @Test
    fun decodeMultiple() {
        val messages = "Lorem ipsum dolor sit amet".split(' ').asSequence()
        val packets = messages
            .map { Message.Decrypted( type = 1, userID = 2, message = it.toByteArray() ) }
            .mapIndexed { idx, message -> Packet( clientID = 3, message = message, packetID = idx.toLong() ) }
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
            .map { Message.Encrypted( type = 1, userID = 2, message = it.toByteArray(), key = key, cipher = cipher ) }
            .mapIndexed { idx, message -> Packet( clientID = 3, message = message, packetID = idx.toLong() ) }
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