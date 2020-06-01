package ua.edu.ukma.csa.network.udp

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import ua.edu.ukma.csa.kotlinx.util.*
import ua.edu.ukma.csa.network.PacketException
import java.net.DatagramPacket
import java.nio.ByteBuffer

@ExperimentalUnsignedTypes
data class UDPPacket(
    val window: UByte,
    val sequenceID: UByte,
    val packetID: ULong,
    val chunk: ByteArray,
    val chunkSize: Int = chunk.size,
    val chunkOffset: Int = 0
) {
    init {
        require(chunkSize >= 0)
        require(chunkOffset >= 0)
    }

    val size get() = PACKET_HEADER + chunk.size.toUInt()

    val data: ByteArray
        get() = ByteBuffer.allocate(size.toInt())
            .put(window)
            .put(sequenceID)
            .putLong(packetID)
            .putInt(chunkSize)
            .put(chunk, chunkOffset, chunkSize)
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
        const val PACKET_SIZE = 1024u
        const val PACKET_HEADER = 14u
        val PACKET_BODY = PACKET_SIZE - PACKET_HEADER
        val MAX_PAYLOAD_SIZE = PACKET_BODY * UByte.MAX_VALUE

        fun from(datagram: DatagramPacket): Either<PacketException, UDPPacket> {
            if (datagram.length < 14) return Left(PacketException.Length(14, datagram.length))
            val buffer = ByteBuffer.wrap(datagram.data, datagram.offset, datagram.length)
            val window = buffer.uByte
            val sequenceID = buffer.uByte
            val packetID = buffer.uLong
            val messageSize = buffer.uInt

            if (datagram.length.toUInt() < PACKET_HEADER + messageSize)
                return Left(PacketException.Length((14u + messageSize).toInt(), datagram.length))

            val chunk = ByteArray(datagram.length - PACKET_HEADER.toInt())
            buffer.get(chunk)
            return Right(UDPPacket(window, sequenceID, packetID, chunk))
        }
    }
}

