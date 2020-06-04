package ua.edu.ukma.csa

import org.junit.jupiter.api.Test
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertLeftType
import ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api.assertRight
import ua.edu.ukma.csa.network.Message
import ua.edu.ukma.csa.network.MessageType
import ua.edu.ukma.csa.network.PacketException
import ua.edu.ukma.csa.network.UserID
import java.security.Key
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator

@ExperimentalUnsignedTypes
class MessageTest {

    private val key: Key
    private val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")

    init {
        val generator = KeyGenerator.getInstance("AES")
        val random = SecureRandom()
        generator.init(128, random)
        key = generator.generateKey()
    }

    @Test
    fun decode() {
        val message = Message.Decrypted(MessageType.OK, UserID.assign(), "hello".toByteArray())
        val decoded = Message.decode<Message.Decrypted>(message.data)
        assertRight(message, decoded)
        assertRight("hello", decoded.map { String(it.message) })
    }

    @Test
    fun decodeSize0() {
        val data = ByteArray(0)
        assertLeftType<PacketException.Length>(Message.decode<Message.Decrypted>(data))
    }

    @Test
    fun encryptedDecode() {
        val message = Message.Decrypted(MessageType.OK, UserID.assign(), "hello".toByteArray())
        val encryptedMessage = message.encrypted(key, cipher)
        val encryptedDecodedMessage = Message.decode<Message.Encrypted>(encryptedMessage.data)
        val decodedMessage = encryptedDecodedMessage.map { it.decrypted(key, cipher) }

        assertRight(encryptedMessage, encryptedDecodedMessage)
        assertRight(message, decodedMessage)
    }

}