package ua.edu.ukma.csa.kotlinx.serialization

import arrow.core.Left
import arrow.core.Right
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy

fun <T> BinaryFormat.fdump(serializer: SerializationStrategy<T>, value: T) = try {
    Right(dump(serializer, value))
} catch (e: SerializationException) {
    Left(e)
}

fun <T> BinaryFormat.fload(deserializer: DeserializationStrategy<T>, value: ByteArray) = try {
    Right(load(deserializer, value))
} catch (e: SerializationException) {
    Left(e)
}