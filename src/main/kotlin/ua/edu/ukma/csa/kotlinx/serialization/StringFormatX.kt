package ua.edu.ukma.csa.kotlinx.serialization

import arrow.core.Left
import arrow.core.Right
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat

fun <T> StringFormat.fparse(deserializer: DeserializationStrategy<T>, jsonString: String) =
    try {
        Right(parse(deserializer, jsonString))
    } catch (e: SerializationException) {
        Left(e)
    }

fun <T> StringFormat.fstringify(serializer: SerializationStrategy<T>, value: T) =
    try {
        Right(stringify(serializer, value))
    } catch (e: SerializationException) {
        Left(e)
    }
