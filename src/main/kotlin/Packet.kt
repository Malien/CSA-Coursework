import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import arrow.core.getOrHandle
import com.github.snksoft.crc.CRC.Parameters
import com.github.snksoft.crc.CRC.calculateCRC
import kotlinx.arrow.core.unwrap
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer

data class Packet<M : Message>(
    val magic: Byte = MAGIC,
    val clientID: Byte,
    val packetID: Long = 0,
    val messageLength: Int,
    val headerCRC: Short,
    val message: M,
    val messageCRC: Short
) {
    constructor(clientID: Byte, message: M, magic: Byte = MAGIC, packetID: Long = 0) : this(
        magic = magic,
        clientID = clientID,
        packetID = packetID,
        messageLength = message.size,
        headerCRC = calculateHeaderCRC(magic, clientID, packetID, message.size),
        message = message,
        messageCRC = calculateMessageCRC(message)
    )

    val size = 18 + message.size

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
                return Left(PacketException.CRCCheck(CRCType.HEADER, expectedHeaderCRC, headerCRC))

            if (length < 18 + messageLength) return Left(PacketException.Length(18 + messageLength, length))

            val messageData = ByteArray(messageLength)
            buffer.get(messageData)

            val message = Message.decode<M>(messageData).unwrap { return@decode it }

            val messageCRC = buffer.short
            val expectedMessageCRC = calculateMessageCRC(message)
            if (expectedMessageCRC != messageCRC)
                return Left(PacketException.CRCCheck(CRCType.MESSAGE, expectedMessageCRC, messageCRC))

            return Right(Packet(magic, clientID, packetID, messageLength, headerCRC, message, messageCRC))
        }

        inline fun <reified M : Message> from(
            stream: DataInputStream,
            seekMagic: Boolean = true
        ): Either<PacketException, Packet<M>> {
            val magic = if (seekMagic) {
                while (stream.readByte() != MAGIC) { }
                MAGIC
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

            if (magic.toInt() != 0x13) return Left(PacketException.Magic(magic))

            val expectedHeaderCRC = calculateHeaderCRC(magic, clientID, packetID, messageLength)
            if (expectedHeaderCRC != headerCRC)
                return Left(PacketException.CRCCheck(CRCType.HEADER, expectedHeaderCRC, headerCRC))

            val message = Message.decode<M>(messageData).unwrap { return@from it }

            val expectedMessageCRC = calculateMessageCRC(message)
            if (expectedMessageCRC != messageCRC)
                return Left(PacketException.CRCCheck(CRCType.MESSAGE, expectedMessageCRC, messageCRC))

            return Right(Packet(magic, clientID, packetID, messageLength, headerCRC, message, messageCRC))
        }

        inline fun <reified M : Message> sequenceFrom(
            stream: InputStream
        ): Sequence<Either<PacketException, Packet<M>>> = sequence {
            val data = DataInputStream(stream)
            try {
                while (true) {
                    yield(from(data))
                }
            } catch (e: EOFException) {
                return@sequence
            }
        }

    }
}
