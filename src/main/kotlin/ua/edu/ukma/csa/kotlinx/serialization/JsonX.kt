package ua.edu.ukma.csa.kotlinx.serialization

import arrow.core.Left
import arrow.core.Right
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json

fun <T> Json.functionalStringify(serializer: SerializationStrategy<T>, value: T) = try {
    Right(stringify(serializer, value))
} catch (e: SerializationException) {
    Left(e)
}

fun <T> Json.functionalParse(deserializer: DeserializationStrategy<T>, string: String) = try {
    Right(parse(deserializer, string))
} catch (e: SerializationException) {
    Left(e)
}
