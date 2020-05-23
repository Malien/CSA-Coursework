package ua.edu.ukma.csa.network

enum class CRCType { HEADER, MESSAGE }

/**
 * Exception that signifies an error while decoding a packet or message from bytes
 */
sealed class PacketException(message: String) : RuntimeException(message) {

    /**
     * Error that signifies an invalid CRC when decoding a packet.
     * @param type whether error occurred in the HEADER crc field or in th MESSAGE
     * @param expected crc value that was expected
     * @param got crc value that was encoded into the message
     */
    data class CRCCheck(val type: CRCType, val expected: Short, val got: Short) :
        PacketException("Invalid ${type.name.toLowerCase()} CRC. Expected $expected, got $got")

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
    data class Length(val expected: Int, val got: Int) :
        PacketException("Expected packet length of $expected, got $got")

    data class InvalidType(val typeID: Int) :
        PacketException("Invalid typeID $typeID")
}