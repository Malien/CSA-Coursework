package ua.edu.ukma.csa.kotlinx.arrow.core

import arrow.core.None
import arrow.core.Option
import arrow.core.Some

inline fun <A> Option<A>.unwrap(whenNone: () -> Nothing) = when(this) {
    is None -> whenNone()
    is Some -> this.t
}
