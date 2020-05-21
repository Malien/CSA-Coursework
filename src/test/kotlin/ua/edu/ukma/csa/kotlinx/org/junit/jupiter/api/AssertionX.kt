package ua.edu.ukma.csa.kotlinx.org.junit.jupiter.api

import arrow.core.Either
import org.junit.jupiter.api.Assertions.assertEquals

fun <L, R> assertRight(expected: R, actual: Either<L, R>) = when (actual) {
    is Either.Right -> assertEquals(expected, actual.b)
    is Either.Left -> assertEquals(expected, actual)
}

fun <L, R> assertLeft(expected: L, actual: Either<L, R>) = when (actual) {
    is Either.Right -> assertEquals(expected, actual)
    is Either.Left -> assertEquals(expected, actual.a)
}

inline fun <reified L> assertLeftType(value: Either<Any, Any?>) = when (value) {
    is Either.Right -> assert(false) { "Expected to have left of type ${L::class}, got $value" }
    is Either.Left -> assert(L::class.isInstance(value.a)) { "Expected left value of type ${L::class}, got ${value.a}" }
}

inline fun <reified R> assertRightType(value: Either<Any?, Any>) = when (value) {
    is Either.Right -> assert(R::class.isInstance(value.b)) { "Expected left value of type ${R::class}, got ${value.b}" }
    is Either.Left -> assert(false) { "Expected to have left of type ${R::class}, got $value" }
}
