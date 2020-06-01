package ua.edu.ukma.csa.network

enum class CRCType { HEADER, MESSAGE }

/**
 * Exception that signifies an error while decoding a packet or message from bytes
 */
sealed class PacketException(message: String, val packetID: Long = 0) : RuntimeException(message) {
    /**
     * Error that signifies an invalid CRC when decoding a packet.
     * @param type whether error occurred in the HEADER crc field or in th MESSAGE
     * @param expected crc value that was expected
     * @param got crc value that was encoded into the message
     */
    class CRCCheck(val type: CRCType, val expected: Short, val got: Short, _packetID: Long = 0) :
        PacketException("Invalid ${type.name.toLowerCase()} CRC. Expected $expected, got $got", _packetID)

    /**
     * Error that signifies an invalid magic byte
     * @param wrongMagic byte that was received instead of magic byte
     */
    data class Magic(val wrongMagic: Byte) :
        PacketException("Wrong magic number. Expected 0x13, got ${Integer.toHexString(wrongMagic.toInt())}")

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