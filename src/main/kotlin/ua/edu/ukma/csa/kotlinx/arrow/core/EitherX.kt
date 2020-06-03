package ua.edu.ukma.csa.kotlinx.arrow.core

import arrow.core.Either

inline fun <L, R> Either<L, R>.unwrap(whenLeft: (Either.Left<L>) -> Nothing) = when(this) {
    is Either.Left -> whenLeft(this)
    is Either.Right -> this.b
}

fun <L : Throwable, R> Either<L, R>.handleWithThrow() = unwrap { throw it.a }

inline fun <L, R, A> Either<L, R>.then(transform: (R) -> Either<L, A>): Either<L, A> = when (this) {
    is Either.Right -> transform(this.b)
    is Either.Left -> this
}
