package ua.edu.ukma.csa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.Key
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator

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
        val message = Message.Decrypted(1, 2, "hello".toByteArray())
        val decoded =
            Message.decode<Message.Decrypted>(
                message.data
            )
        assertEquals(message, decoded)
        assertEquals("hello", String(decoded.message))
    }

    @Test
    fun decodeSize0() {
        val data = ByteArray(0)
        assertThrows<PacketException.Length> {
            Message.decode<Message.Decrypted>(
                data
            )
        }
    }

    @Test
    fun encryptedDecode() {
        val message = Message.Decrypted(1, 2, "hello".toByteArray())
        val encryptedMessage = message.encrypted(key, cipher)
        val encryptedDecodedMessage =
            Message.decode<Message.Encrypted>(
                encryptedMessage.data
            )
        val decodedMessage = encryptedDecodedMessage.decrypted(key, cipher)

        assertEquals(encryptedDecodedMessage, encryptedMessage)
        assertEquals(message, decodedMessage)
    }

}