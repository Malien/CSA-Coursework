package ua.edu.ukma.csa.network.udp

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import ua.edu.ukma.csa.kotlinx.arrow.core.handleWithThrow
import ua.edu.ukma.csa.kotlinx.div
import ua.edu.ukma.csa.network.*
import java.io.Closeable
import java.net.*
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.ceil

@ExperimentalUnsignedTypes
class UDPServer(port: Int, bindAddress: InetAddress = InetAddress.getByName("0.0.0.0")) : Closeable {
    val socket = DatagramSocket(port, bindAddress)
    init {
        socket.soTimeout = TIMEOUT
    }

    @Volatile
    var shouldStop = false

    class PacketBlob(val packets: Array<UDPPacket?>, var count: UByte = 0u) {
        constructor(window: UByte) : this(arrayOfNulls(window.toInt()))

        operator fun get(idx: UByte) = packets[idx.toInt()]
        operator fun set(idx: UByte, packet: UDPPacket) {
            packets[idx.toInt()] = packet
        }

        val window get() = packets.firstOrNull()?.window
    }

    val parts = HashMap<SocketAddress, TreeMap<ULong, PacketBlob>>()

    data class UDPRequest(val packet: ByteArray, val address: SocketAddress)

    private fun receive() = sequence {
        val buffer = ByteArray(UDPPacket.PACKET_SIZE.toInt())
        val datagram = DatagramPacket(buffer, buffer.size)

        loop@ while (true) {
            try {
                if (shouldStop) break@loop
                socket.receive(datagram)
                val packet = UDPPacket.from(datagram)
                if (packet is Either.Right) {
                    val blob = parts[datagram.socketAddress]?.get(packet.b.packetID)

                    if (blob == null) {
                        if (packet.b.window == 1.toUByte()) {
                            yield(UDPRequest(packet.b.chunk, datagram.socketAddress))
                        } else {
                            val userData = parts.getOrPut(datagram.socketAddress) { TreeMap<ULong, PacketBlob>() }
                            val newBlob = PacketBlob(packet.b.window)
                            if (packet.b.sequenceID > packet.b.window) continue@loop // TODO: send error message back
                            newBlob[packet.b.sequenceID] = packet.b
                            newBlob.count++
                            userData[packet.b.packetID] = newBlob
                        }
                    } else {
                        if (packet.b.window != blob.window) continue@loop // TODO: send error message back
                        if (packet.b.sequenceID > packet.b.window) continue@loop // TODO: send error message back
                        if (blob[packet.b.sequenceID] == null) {
                            blob.count++
                        }
                        blob[packet.b.sequenceID] = packet.b
                        if (blob.count == packet.b.window) {
                            val combined = ByteArray(blob.packets.sumBy { it!!.size.toInt() })
                            blob.packets.fold(0) { written, blobPacket ->
                                blobPacket!!.chunk.copyInto(combined, destinationOffset = written)
                                written + blobPacket.chunk.size
                            }
                            parts[datagram.socketAddress]!!.remove(packet.b.packetID)
                            yield(UDPRequest(combined, datagram.socketAddress))
                        }
                    }
                }
            } catch (ignore: SocketTimeoutException) {
                println(shouldStop)
            }
        }
    }

    fun serve() {
        shouldStop = false
        for ((data, address) in receive()) {
            when (val request = Packet.decode<Message.Decrypted>(data)) {
                is Either.Right -> {
                    val response = handlePacket(request.b)
                    send(response, address)
                }
                is Either.Left -> {
                    val response = Response.Error(request.a.message ?: "").toMessage().handleWithThrow()
                    val packet = Packet(clientID = 0, message = response, packetID = request.a.packetID)
                    send(packet, address)
                }
            }
        }
    }

    inline fun <reified M : Message> send(packet: Packet<M>, to: SocketAddress) =
        send(to, packetID = packet.packetID.toULong(), data = packet.data)

    fun send(
        to: SocketAddress,
        packetID: ULong,
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size
    ): Either<RuntimeException, Unit> {
        if (length.toUInt() > UDPPacket.MAX_PAYLOAD_SIZE) return Left(
            RuntimeException(
                "Data is too large to be transmitted. Size: $length, max: ${UDPPacket.MAX_PAYLOAD_SIZE}"
            )
        )
        val window = ceil(length.toDouble() / UDPPacket.PACKET_BODY).toUInt()
        for (i in 0u..window) {
            val dataSplit = data.copyOfRange(
                fromIndex = offset + (i * UDPPacket.PACKET_BODY).toInt(),
                toIndex = offset + ((i + 1u) * UDPPacket.PACKET_BODY).toInt().coerceAtLeast(data.size)
            )
            val udpPacket = UDPPacket(
                window = window.toUByte(),
                sequenceID = i.toUByte(),
                packetID = packetID,
                chunk = dataSplit,
                chunkOffset = offset + (i * UDPPacket.PACKET_BODY).toInt(),
                chunkSize = UDPPacket.PACKET_BODY.toInt().coerceAtLeast(data.size)
            )
            socket.send(DatagramPacket(udpPacket.data, udpPacket.size.toInt(), to))
        }
        return Right(Unit)
    }

    override fun close() {
        shouldStop = true
        socket.close()
    }

    companion object {
        const val TIMEOUT = 1000
    }

}
