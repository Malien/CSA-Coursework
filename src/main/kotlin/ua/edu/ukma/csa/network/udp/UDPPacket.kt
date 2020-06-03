package ua.edu.ukma.csa.network.udp

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import ua.edu.ukma.csa.kotlinx.java.util.*
import ua.edu.ukma.csa.network.PacketException
import ua.edu.ukma.csa.network.udp.UDPPacket.Companion.MAGIC
import ua.edu.ukma.csa.network.udp.UDPPacket.Companion.PACKET_BODY
import ua.edu.ukma.csa.network.udp.UDPPacket.Companion.PACKET_HEADER
import ua.edu.ukma.csa.network.udp.UDPPacket.Companion.PACKET_SIZE
import java.net.DatagramPacket
import java.nio.ByteBuffer

/**
 * Data structure that represents UDP packets used to transfer larger messages. It contains needed information to
 * reassemble complete message. The maximum length of UDPPacket is [PACKET_SIZE]. [PACKET_HEADER] bytes are used to store
 * header information, which leaves [PACKET_BODY] bytes for the message chunk. [PACKET_SIZE] should be the same across
 * clients and servers. Packet's structure is as follows:
 * ```
 * |   1 byte   | 1 byte |   1 byte   | 8 bytes  | ...rest (up until PACKET_SIZE) |
 * | magic byte | window | sequenceID | packetID |             chunk              |
 * ```
 * magic byte is a constant value of [MAGIC]. It is used to quickly drop wrongly formed or corrupted data.
 * @param window specifies the length of the collection of packets that form complete message (window)
 * @param sequenceID specifies the index of this packet in the window
 * @param packetID specifies the packet id which will be formed by the complete window. Aka. all packets in the same
 *                 window should have the same `packetID`
 * @param chunk the body of packet
 */
data class UDPPacket(
    val window: UByte,
    val sequenceID: UByte,
    val packetID: ULong,
    val chunk: ByteArray,
    val chunkLength: Int = chunk.size,
    val chunkOffset: Int = 0
) {
    init {
        require(chunkLength >= 0)
        require(chunkOffset >= 0)
    }

    /**
     * Size of the serialized packet
     */
    val size get() = PACKET_HEADER + chunk.size.toUInt()

    /**
     * Serialized packet ready to be transferred through the network
     */
    val data: ByteArray
        get() = ByteBuffer.allocate(size.toInt())
            .put(MAGIC)
            .put(window)
            .put(sequenceID)
            .putLong(packetID)
            .put(chunk, chunkOffset, chunkLength)
            .array()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UDPPacket

        if (window != other.window) return false
        if (sequenceID != other.sequenceID) return false
        if (!chunk.contentEquals(other.chunk)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = window.toUInt()
        result = 31u * result + sequenceID
        result = 31u * result + chunk.contentHashCode().toUInt()
        return result.toInt()
    }

    companion object {
        private const val MAGIC: Byte = 0x14
        const val PACKET_SIZE = 1024u
        const val PACKET_HEADER = 11u
        val PACKET_BODY = PACKET_SIZE - PACKET_HEADER
        val MAX_PAYLOAD_SIZE = PACKET_BODY * UByte.MAX_VALUE

        /**
         * Forms UDPPacket from the [datagram][DatagramPacket].
         * @param datagram datagram from which [UDPPacket] will be produced
         * @return [Either] a [PacketException] in case of deserialization error, or a [UDPPacket] in case of success
         */
        fun from(datagram: DatagramPacket): Either<PacketException, UDPPacket> =
            from(datagram.data, datagram.offset, datagram.length)

        /**
         * Form UDPPacket from the bytes
         * @param data array of bytes which contains serialized message
         * @param offset offset in the given array. Default 0.
         * @param length length of serialized packet in array. Default is data.size.
         * @return [Either] a [PacketException] in case of deserialization error, or a [UDPPacket] in case of success
         */
        fun from(data: ByteArray, offset: Int = 0, length: Int = data.size): Either<PacketException, UDPPacket> {
            if (length < PACKET_HEADER.toInt())
                return Left(PacketException.Length(14, length))
            val buffer = ByteBuffer.wrap(data, offset, length)
            val magic = buffer.byte
            if (magic != MAGIC) return Left(PacketException.Magic(MAGIC, magic))
            val window = buffer.uByte
            val sequenceID = buffer.uByte
            val packetID = buffer.uLong

            val chunk = ByteArray(length - PACKET_HEADER.toInt())
            buffer.get(chunk)
            return Right(UDPPacket(window, sequenceID, packetID, chunk))
        }
    }
}

