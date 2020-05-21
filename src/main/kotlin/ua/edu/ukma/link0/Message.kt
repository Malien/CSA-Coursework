package ua.edu.ukma.link0

import java.nio.ByteBuffer
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec

sealed class Message(
    val type: Int,
    val userID: Int,
    val message: ByteArray
) {
    val size get() = 8 + message.size

    val data: ByteArray
        get() = ByteBuffer.allocate(size)
            .putInt(type)
            .putInt(userID)
            .put(message)
            .array()

    operator fun component1() = type
    operator fun component2() = userID
    operator fun component3() = message

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Message

        if (type != other.type) return false
        if (userID != other.userID) return false
        if (!message.contentEquals(other.message)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + userID
        result = 31 * result + message.contentHashCode()
        return result
    }

    override fun toString() = "com.link0.Message(type=$type, userID=$userID, message=${message.contentToString()})"

    class Decrypted(
        type: Int,
        userID: Int,
        message: ByteArray
    ) : Message(type, userID, message) {

        fun encrypted(
            encryptionKey: Key,
            cipher: Cipher,
            iv: IvParameterSpec = IvParameterSpec(ByteArray(16))
        ): Encrypted {
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, iv)
            val encryptedMessage = cipher.doFinal(message)
            return Encrypted(type, userID, encryptedMessage)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            if (!super.equals(other)) return false
            return true
        }

        override fun toString() = "com.link0.Message.Decrypted(type=$type, userID=$userID, message=${message.contentToString()})"

    }

    class Encrypted(
        type: Int,
        userID: Int,
        encryptedMessage: ByteArray
    ) : Message(type, userID, encryptedMessage) {

        constructor(
            type: Int,
            userID: Int,
            message: ByteArray,
            key: Key,
            cipher: Cipher,
            iv: IvParameterSpec = IvParameterSpec(ByteArray(16))
        ) : this(
            type, userID,
            encrypt(message, key, cipher, iv)
        )

        fun decrypted(
            decryptionKey: Key,
            cipher: Cipher,
            iv: IvParameterSpec = IvParameterSpec(ByteArray(16))
        ): Decrypted {
            cipher.init(Cipher.DECRYPT_MODE, decryptionKey, iv)
            val decryptedMessage = cipher.doFinal(message)
            return Decrypted(type, userID, decryptedMessage)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            if (!super.equals(other)) return false
            return true
        }

        override fun toString() = "com.link0.Message.Encrypted(type=$type, userID=$userID, message=${message.contentToString()})"

        companion object {
            fun encrypt(
                message: ByteArray,
                encryptionKey: Key,
                cipher: Cipher,
                iv: IvParameterSpec = IvParameterSpec(ByteArray(16))
            ): ByteArray {
                cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, iv)
                return cipher.doFinal(message)
            }
        }

    }

    companion object {
        inline fun <reified M : Message> decode(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): M {
            if (length < 8) throw PacketException.Length(8, length)

            val buffer = ByteBuffer.wrap(bytes, offset, length)
            val type = buffer.int
            val userID = buffer.int
            val message = ByteArray(length - 8)
            buffer.get(message)

            return when (M::class) {
                Encrypted::class -> Encrypted(
                    type,
                    userID,
                    message
                ) as M
                Decrypted::class -> Decrypted(
                    type,
                    userID,
                    message
                ) as M
                else -> throw RuntimeException("Unknown message type")
            }
        }
    }

}
