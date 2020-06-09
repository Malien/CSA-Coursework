package ua.edu.ukma.csa.kotlinx.arrow.core

import arrow.core.andThen
import arrow.core.compose

fun <A, B, C> compose(f1: (B) -> C, f2: (A) -> B) = f1 compose f2

fun <A, B, C> andThen(f1: (A) -> B, f2: (B) -> C) = f1 andThen f2