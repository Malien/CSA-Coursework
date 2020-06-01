package ua.edu.ukma.csa.kotlinx

@ExperimentalUnsignedTypes
infix operator fun Double.div(value: UByte) = this / value.toShort()

@ExperimentalUnsignedTypes
infix operator fun Double.div(value: UShort) = this / value.toInt()

@ExperimentalUnsignedTypes
infix operator fun Double.div(value: UInt) = this / value.toLong()