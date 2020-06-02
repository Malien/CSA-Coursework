package ua.edu.ukma.csa.kotlinx.java.util

import java.nio.ByteBuffer

fun ByteBuffer.put(byte: UByte): ByteBuffer = put(byte.toByte())

fun ByteBuffer.put(array: ByteArray, offset: UInt, length: UInt): ByteBuffer =
    put(array, offset.toInt(), length.toInt())

fun ByteBuffer.putShort(short: UShort): ByteBuffer = putShort(short.toShort())

fun ByteBuffer.putInt(int: UInt): ByteBuffer = putInt(int.toInt())

fun ByteBuffer.putLong(long: ULong): ByteBuffer = putLong(long.toLong())

val ByteBuffer.byte get() = get()

val ByteBuffer.uByte get() = get().toUByte()

val ByteBuffer.uShort get() = short.toUShort()

val ByteBuffer.uInt get() = int.toUInt()

val ByteBuffer.uLong get() = long.toULong()
