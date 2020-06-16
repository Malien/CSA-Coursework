package ua.edu.ukma.csa.network

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import ua.edu.ukma.csa.kotlinx.arrow.core.unwrap
import ua.edu.ukma.csa.kotlinx.java.util.putInt
import ua.edu.ukma.csa.kotlinx.java.util.uInt
import java.nio.ByteBuffer
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec

/**
 * Class that contains domain specific data.
 *
 * Contains two variants: [Message.Encrypted] and [Message.Decrypted].
 * Encryption only affects `message` field.
 * `type` and `userID` stays unencrypted no matter what.
 */
sealed class Message(
    val type: MessageType,
    val userID: UserID,
    val message: ByteArray = ByteArray(0)
) {
    /**
     * Size of binary representation of message (Length of byte-encoded message)
     */
    val size get() = 8 + message.size

    /**
     * Byte-encoded message. Can be used to serialize message
     */
    val data: ByteArray
        get() = ByteBuffer.allocate(size)
            .putInt(type.typeID)
            .putInt(userID.id)
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
        var result = type.hashCode()
        result = 31 * result + userID.hashCode()
        result = 31 * result + (message.contentHashCode())
        return result
    }

    override fun toString() = "com.link0.Message(type=$type, userID=$userID, message=${message.contentToString()})"

    /**
     * Representation of unencrypted message.
     *
     * Contains `encrypted` method for producing encrypted version of said message.
     */
    class Decrypted(
        type: MessageType,
        userID: UserID,
        message: ByteArray = ByteArray(0)
    ) : Message(type, userID, message) {

        /**
         * Encrypts message.
         * @param encryptionKey key which will be used for encryption
         * @param cipher specified to encrypt message
         * @param iv initialization vector used to offset message when encrypting.
         * _Defaults to `IvParameterSpec(ByteArray(16))`_
         * @return encrypted message of type [Message.Encrypted]
         */
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

        override fun hashCode() = super.hashCode()

        override fun toString() =
            "com.link0.Message.Decrypted(type=$type, userID=$userID, message=${message.contentToString()})"

    }

    /**
     * Representation of encrypted message.
     *
     * Contains `decrypted` method for producing unencrypted version of said message.
     */
    class Encrypted(
        type: MessageType,
        userID: UserID,
        encryptedMessage: ByteArray
    ) : Message(type, userID, encryptedMessage) {

        /**
         * Constructs already encrypted message with key, cipher and iv specified.
         */
        constructor(
            type: MessageType,
            userID: UserID,
            message: ByteArray = ByteArray(0),
            key: Key,
            cipher: Cipher,
            iv: IvParameterSpec = IvParameterSpec(ByteArray(16))
        ) : this(
            type, userID,
            encrypt(message, key, cipher, iv)
        )

        /**
         * Encrypts message.
         * @param decryptionKey key which will be used for decryption
         * @param cipher specified to decrypt message
         * @param iv initialization vector used to offset message when decrypting.
         * _Defaults to `IvParameterSpec(ByteArray(16))`_
         * @return decrypted message of type [Message.Decrypted]
         */
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

        override fun hashCode() = super.hashCode()

        override fun toString() =
            "com.link0.Message.Encrypted(type=$type, userID=$userID, message=${message.contentToString()})"

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
        /**
         * Decodes message from an array of bytes, starting at offset and end is specified by length
         *
         * Takes a reified generic type parameter M, which determines which type of message should be produced.
         * M has to be an subclass of [Message].
         *
         * For e.g. * `decode<Message.Encrypted>` will produce a [encrypted message][Message.Encrypted] ,
         * where is `decode<Message.Decrypted>` will produce a [decrypted one][Message.Decrypted]
         *
         *  @param bytes an array from which message supposed to be decoded
         *  @param offset offset to an provided array. Default is 0
         *  @param length length of data to be decoded from array. Default is bytes.size
         *  @return Either an PacketException or a Message (type of which is specified as generic parameter M>
         */
        inline fun <reified M : Message> decode(
            bytes: ByteArray,
            offset: Int = 0,
            length: Int = bytes.size
        ): Either<PacketException, M> {
            if (length < 8) return (Left(PacketException.Length(8, length)))

            val buffer = ByteBuffer.wrap(bytes, offset, length)
            val typeID = buffer.int
            val type = MessageType.fromID(typeID).unwrap {
                return@decode Left(PacketException.InvalidType(typeID))
            }
            val userID = UserID(buffer.uInt)
            val message = ByteArray(length - 8)
            buffer.get(message)

            return Right(
                when (M::class) {
                    Encrypted::class -> Encrypted(type, userID, message) as M
                    Decrypted::class -> Decrypted(type, userID, message) as M
                    Message::class -> Decrypted(type, userID, message) as M
                    else -> throw RuntimeException("Unknown message type")
                }
            )
        }
    }

}

