package com.link0

enum class CRCType { HEADER, MESSAGE }

sealed class PacketException(message: String) : RuntimeException(message) {

    class CRCCheck(val type: CRCType, val expected: Short, val got: Short) :
        PacketException("Invalid ${type.name.toLowerCase()} CRC. Expected $expected, got $got")

    class Magic(val wrongMagic: Byte) :
        PacketException("Wrong magic number. Expected 0x13, got ${Integer.toHexString(wrongMagic.toInt())}")

    class Length(val expected: Int, val got: Int) :
        PacketException("Expected packet length of $expected, got $got")
}