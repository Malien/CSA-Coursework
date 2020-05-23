package ua.edu.ukma.csa.kotlinx

import java.util.*

fun Random.next(range: IntRange) = nextInt(range.size) + range.first
fun Random.next(range: LongRange) = nextLong().rem(range.size) + range.first

fun Random.nextByte(from: Byte, to: Byte) = next(from..to).toByte()
fun Random.nextShort(from: Short, to: Short) = next(from..to).toShort()
fun Random.nextInt(from: Int, to: Int) = next(from..to)
fun Random.nextLong(from: Long, to: Long) = next(from..to)
