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

/**
 * Send [Packet] through [DatagramSocket] to the specified address. Packet will be split up into [UDPPacket]s as a
 * which is used as a transport medium.
 *
 * @param packet packet which to send through the network
 * @param to where to send said packet
 * @return [Either] [RuntimeException] that signifies that packet is too large, or a [Unit]
 */
inline fun <reified M : Message> DatagramSocket.send(packet: Packet<M>, to: SocketAddress) =
    splitData(packet.data, packetID = packet.packetID).map { list ->
        list.asSequence()
            .map { it.data }
            .map { DatagramPacket(it, it.size, to) }
            .forEach(::send)
    }

/**
 * Split chunk of data into the list of [UDPPacket]s
 * @param data byte array that will be encoded into UDPPackets
 * @param packetID unique identifier (usually autoincremented)
 * @param offset offset into the data array. Default is `0`
 * @param length length of the data in array. Default is `data.size`
 */
fun splitData(
    data: ByteArray,
    packetID: ULong,
    offset: Int = 0,
    length: Int = data.size
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
            chunkLength = UDPPacket.PACKET_BODY.toInt()
                .coerceAtMost(data.size - offset - (it * UDPPacket.PACKET_BODY).toInt())
        )
    })
}
