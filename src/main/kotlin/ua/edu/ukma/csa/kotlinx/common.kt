package ua.edu.ukma.csa.kotlinx

inline fun <T, R> T?.transformNotNull(transform: (T) -> R): R? = if (this == null) null else transform(this)
