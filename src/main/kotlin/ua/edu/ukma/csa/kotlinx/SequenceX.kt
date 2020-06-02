package ua.edu.ukma.csa.kotlinx

inline fun <T> Sequence<T>.peek(crossinline func: (T) -> Unit) = map {
    func(it)
    it
}