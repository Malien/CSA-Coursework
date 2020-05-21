package ua.edu.ukma.link0.kotlinx.arrow.core

import arrow.core.Either

fun <L : Throwable, R> Either<L, R>.handleWithThrow() = when(this) {
    is Either.Left -> throw this.a
    is Either.Right -> this.b
}

inline fun <L, R> Either<L, R>.unwrap(whenLeft: (Either.Left<L>) -> Nothing) = when(this) {
    is Either.Left -> whenLeft(this)
    is Either.Right -> this.b
}