package ua.edu.ukma.csa.kotlinx.util

import java.nio.ByteBuffer

@ExperimentalUnsignedTypes
fun ByteBuffer.put(byte: UByte): ByteBuffer = put(byte.toByte())

@ExperimentalUnsignedTypes
fun ByteBuffer.put(array: ByteArray, offset: UInt, length: UInt): ByteBuffer =
    put(array, offset.toInt(), length.toInt())

@ExperimentalUnsignedTypes
fun ByteBuffer.putShort(short: UShort): ByteBuffer = putShort(short.toShort())

@ExperimentalUnsignedTypes
fun ByteBuffer.putInt(int: UInt): ByteBuffer = putInt(int.toInt())

@ExperimentalUnsignedTypes
fun ByteBuffer.putLong(long: ULong): ByteBuffer = putLong(long.toLong())

val ByteBuffer.byte get() = get()

@ExperimentalUnsignedTypes
val ByteBuffer.uByte get() = get().toUByte()

@ExperimentalUnsignedTypes
val ByteBuffer.uShort get() = short.toUShort()

@ExperimentalUnsignedTypes
val ByteBuffer.uInt get() = int.toUInt()

@ExperimentalUnsignedTypes
val ByteBuffer.uLong get() = long.toULong()
