package ua.edu.ukma.csa.network

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import com.github.snksoft.crc.CRC.Parameters
import com.github.snksoft.crc.CRC.calculateCRC
import ua.edu.ukma.csa.kotlinx.arrow.core.unwrap
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Class that represents a packet that is to be transmitted or received over the network
 *
 * @param M type of a message that packet contains within
 */
data class Packet<M : Message>(
    val magic: Byte = MAGIC,
    val clientID: Byte,
    val packetID: Long = 0,
    val messageLength: Int,
    val headerCRC: Short,
    val message: M,
    val messageCRC: Short
) {
    constructor(clientID: Byte, message: M, packetID: Long = 0) : this(
        clientID = clientID,
        packetID = packetID,
        messageLength = message.size,
        headerCRC = calculateHeaderCRC(
            MAGIC,
            clientID,
            packetID,
            message.size
        ),
        message = message,
        messageCRC = calculateMessageCRC(message)
    )

    /**
     * Size of an serialized packet
     */
    val size = 18 + message.size

    /**
     * A byte array representing serialized packet.
     */
    val data: ByteArray
        get() = ByteBuffer.allocate(size)
            .put(magic)
            .put(clientID)
            .putLong(packetID)
            .putInt(messageLength)
            .putShort(headerCRC)
            .put(message.data)
            .putShort(messageCRC)
            .array()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Packet<*>

        if (magic != other.magic) return false
        if (clientID != other.clientID) return false
        if (packetID != other.packetID) return false
        if (messageLength != other.messageLength) return false
        if (headerCRC != other.headerCRC) return false
        if (message != other.message) return false
        if (messageCRC != other.messageCRC) return false

        return true
    }

    override fun hashCode(): Int {
        var result = magic.toInt()
        result = 31 * result + clientID
        result = 31 * result + packetID.hashCode()
        result = 31 * result + messageLength
        result = 31 * result + headerCRC
        result = 31 * result + message.hashCode()
        result = 31 * result + messageCRC
        return result
    }

    companion object {
        const val MAGIC: Byte = 0x13

        fun headerData(magic: Byte, clientID: Byte, packetID: Long, messageLength: Int): ByteArray =
            ByteBuffer.allocate(14)
                .put(magic)
                .put(clientID)
                .putLong(packetID)
                .putInt(messageLength)
                .array()

        fun calculateHeaderCRC(magic: Byte, clientID: Byte, packetID: Long, messageLength: Int) =
            calculateCRC(Parameters.CRC16, headerData(magic, clientID, packetID, messageLength)).toShort()

        fun calculateMessageCRC(message: Message) =
            calculateCRC(Parameters.CRC16, message.data).toShort()

        /**
         * Decodes byte array into a packet object.
         * Takes a reified generic type parameter M, which determines which type of message should be produced.
         * M has to be an subclass of Message.
         * For e.g. decode<Message.Encrypted> will produce a packet with an encrypted message inside,
         * where is decode<Message.Decrypted> will produce a packet with a decrypted one
         * @param bytes an byte array from which message is to be decoded
         * @param offset an offset into an array, from where packet bytes begin
         * @param length a length of serialized packet
         * @return Either an PacketException in case of an error or Packet<M> in case of successful deserialization
         */
        inline fun <reified M : Message> decode(
            bytes: ByteArray,
            offset: Int = 0,
            length: Int = bytes.size
        ): Either<PacketException, Packet<M>> {
            if (length < 18) return Left(PacketException.Length(18, length))

            val buffer = ByteBuffer.wrap(bytes, offset, length)

            val magic = buffer.get()
            if (magic.toInt() != 0x13) return Left(PacketException.Magic(magic))

            val clientID = buffer.get()
            val packetID = buffer.long
            val messageLength = buffer.int

            val headerCRC = buffer.short
            val expectedHeaderCRC = calculateHeaderCRC(magic, clientID, packetID, messageLength)
            if (expectedHeaderCRC != headerCRC)
                return Left(PacketException.CRCCheck(CRCType.HEADER, expectedHeaderCRC, headerCRC, packetID))

            if (length < 18 + messageLength)
                return Left(PacketException.Length(18 + messageLength, length, packetID))

            val messageData = ByteArray(messageLength)
            buffer.get(messageData)
            val message = Message.decode<M>(messageData).unwrap { return@decode it }

            val messageCRC = buffer.short
            val expectedMessageCRC = calculateMessageCRC(message)
            if (expectedMessageCRC != messageCRC)
                return Left(PacketException.CRCCheck(CRCType.MESSAGE, expectedMessageCRC, messageCRC, packetID))

            return Right(Packet(magic, clientID, packetID, messageLength, headerCRC, message, messageCRC))
        }

        /**
         * Decodes a packet object from the stream.
         * Takes a reified generic type parameter M, which determines which type of message should be produced.
         * M has to be an subclass of Message.
         * For e.g. from<Message.Encrypted> will produce a packet with an encrypted message inside,
         * where is from<Message.Decrypted> will produce a packet with a decrypted one
         * @param stream stream from which decode packet
         * @param seekMagic whether or not to look for a magic byte before attempting to decode a packet
         * @return Either an PacketException in case of an error or Packet<M> in case of successful deserialization
         */
        inline fun <reified M : Message> from(
            stream: DataInputStream,
            seekMagic: Boolean = true
        ): Either<PacketException, Packet<M>> {
            val magic = if (seekMagic) {
                var currentByte = stream.readByte()
                while (currentByte != MAGIC) currentByte = stream.readByte()
                currentByte
            } else {
                stream.readByte()
            }

            val clientID = stream.readByte()
            val packetID = stream.readLong()
            val messageLength = stream.readInt()
            val headerCRC = stream.readShort()
            val messageData = ByteArray(messageLength)
            stream.read(messageData)
            val messageCRC = stream.readShort()

            if (magic.toInt() != 0x13) throw PacketException.Magic(magic)

            val expectedHeaderCRC = calculateHeaderCRC(magic, clientID, packetID, messageLength)
            if (expectedHeaderCRC != headerCRC)
                return Left(
                    PacketException.CRCCheck(
                        CRCType.HEADER,
                        expectedHeaderCRC,
                        headerCRC
                    )
                )

            val message = Message.decode<M>(messageData).unwrap { return@from it }

            val expectedMessageCRC =
                calculateMessageCRC(message)
            if (expectedMessageCRC != messageCRC)
                return Left(
                    PacketException.CRCCheck(
                        CRCType.MESSAGE,
                        expectedMessageCRC,
                        messageCRC
                    )
                )

            return Right(Packet(magic, clientID, packetID, messageLength, headerCRC, message, messageCRC))
        }

        /**
         * Converts a stream to a sequence of packets
         * @param stream stream from which packets will be decoded
         * @return a sequence of either a packet exception or a packet itself
         */
        inline fun <reified M : Message> sequenceFrom(stream: InputStream) = sequence {
            val data = DataInputStream(stream)
            while (true) {
                try {
                    while (true) yield(from<M>(data))
                } catch (e: EOFException) {
                    return@sequence
                }
            }
        }

    }
}
