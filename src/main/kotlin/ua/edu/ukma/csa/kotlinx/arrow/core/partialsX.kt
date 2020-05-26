package ua.edu.ukma.csa.kotlinx.arrow.core

import arrow.syntax.function.partially1

fun <P1, P2, R> ((P1, P2) -> R).bind(p1: P1, p2: P2): () -> R = { this(p1, p2) }
fun <P1, P2, R> ((P1, P2) -> R).bind(p1: P1) = partially1(p1)

fun <P1, P2, P3, R> ((P1, P2, P3) -> R).bind(p1: P1, p2: P2, p3: P3): () -> R = { this(p1, p2, p3) }
fun <P1, P2, P3, R> ((P1, P2, P3) -> R).bind(p1: P1, p2: P2): (p3: P3) -> R = { p3 -> this(p1, p2, p3) }
fun <P1, P2, P3, R> ((P1, P2, P3) -> R).bind(p1: P1) = partially1(p1)
