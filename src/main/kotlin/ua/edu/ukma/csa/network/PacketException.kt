package ua.edu.ukma.csa.network

enum class CRCType { HEADER, MESSAGE }

/**
 * Exception that signifies an error while decoding a packet or message from bytes
 */
sealed class PacketException(message: String, val packetID: Long = 0) : RuntimeException(message) {
    /**
     * Error that signifies an invalid CRC when decoding a packet.
     * @param type whether error occurred in the [header][CRCType.HEADER] crc field or in the [message][CRCType.MESSAGE]
     * @param expected crc value that was expected
     * @param got crc value that was encoded into the message
     */
    class CRCCheck(val type: CRCType, val expected: Short, val got: Short, _packetID: Long = 0) :
        PacketException("Invalid ${type.name.toLowerCase()} CRC. Expected $expected, got $got", _packetID)

    /**
     * Error that signifies an invalid magic byte
     * @param expected byte that was expected as a magic byte
     * @param got byte that was received instead of magic byte
     */
    data class Magic(val expected: Byte, val got: Byte) :
        PacketException("Wrong magic number. Expected $expected, got $got")

    /**
     * Error that signifies an invalid packet or message length
     * @param expected the minimum length or the expected length of a message or a packet
     * @param got length provided by the calling function
     */
    class Length(val expected: Int, val got: Int, _packetID: Long = 0) :
        PacketException("Expected packet length of $expected, got $got", _packetID)

    /**
     * Error that signifies an invalid message type field
     * @param typeID identifier of received message type
     */
    class InvalidType(val typeID: Int, _packetID: Long = 0) :
        PacketException("Invalid typeID $typeID", _packetID)
}