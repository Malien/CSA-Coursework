package ua.edu.ukma.csa.network.udp

/**
 * Container for managing packets that are in the process of being assembled (windows).
 * @param packets array of packets that are a part of the same window. Size of array is specified by the size of
 *                window itself
 * @param received amount of packets from window that are already received.
 */
class UDPWindow(val packets: Array<UDPPacket?>, var received: UByte = 0u) {
    /**
     * Constructs an empty window container with the expected size of window
     * @param window the expected size of window
     */
    constructor(window: UByte) : this(arrayOfNulls(window.toInt()))

    operator fun get(idx: UByte) = packets[idx.toInt()]
    operator fun set(idx: UByte, packet: UDPPacket) {
        packets[idx.toInt()] = packet
    }

    val count get() = packets.size.toUByte()
}