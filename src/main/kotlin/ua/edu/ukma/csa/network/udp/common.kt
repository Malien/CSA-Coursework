package ua.edu.ukma.csa.network.udp

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import ua.edu.ukma.csa.kotlinx.div
import ua.edu.ukma.csa.network.Message
import ua.edu.ukma.csa.network.Packet
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketAddress
import kotlin.math.ceil

@ExperimentalUnsignedTypes
inline fun <reified M : Message> DatagramSocket.send(packet: Packet<M>, to: SocketAddress) =
    splitData(packet.data, packetID = packet.packetID.toULong()).map { list ->
        list.asSequence()
            .map { it.data }
            .map { DatagramPacket(it, it.size, to) }
            .forEach(::send)
    }

@ExperimentalUnsignedTypes
fun splitData(
    data: ByteArray,
    offset: Int = 0,
    length: Int = data.size,
    packetID: ULong
): Either<RuntimeException, List<UDPPacket>> {
    if (length.toUInt() > UDPPacket.MAX_PAYLOAD_SIZE) return Left(
        RuntimeException(
            "Data is too large to be transmitted. Size: $length, max: ${UDPPacket.MAX_PAYLOAD_SIZE}"
        )
    )
    val window = ceil(length.toDouble() / UDPPacket.PACKET_BODY).toUInt()

    return Right((0u until window).map {
        UDPPacket(
            window = window.toUByte(),
            sequenceID = it.toUByte(),
            packetID = packetID,
            chunk = data,
            chunkOffset = offset + (it * UDPPacket.PACKET_BODY).toInt(),
            chunkSize = UDPPacket.PACKET_BODY.toInt()
                .coerceAtMost(data.size - offset - (it * UDPPacket.PACKET_BODY).toInt())
        )
    })
}
