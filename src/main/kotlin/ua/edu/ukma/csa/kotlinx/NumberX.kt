package ua.edu.ukma.csa.kotlinx

infix operator fun Double.div(value: UByte) = this / value.toShort()

infix operator fun Double.div(value: UShort) = this / value.toInt()

infix operator fun Double.div(value: UInt) = this / value.toLong()