package ua.edu.ukma.csa.kotlinx

inline fun <T> Array<T>.binarySearch(fromIndex: Int = 0, toIndex: Int = size, comparison: (T) -> Int): Int {
    when {
        fromIndex > toIndex ->
            throw IllegalArgumentException("fromIndex ($fromIndex) is greater than toIndex ($toIndex).")
        fromIndex < 0 ->
            throw IndexOutOfBoundsException("fromIndex ($fromIndex) is less than zero.")
        toIndex > size ->
            throw IndexOutOfBoundsException("toIndex ($toIndex) is greater than size ($size).")
    }

    var low = fromIndex
    var high = toIndex - 1

    while (low <= high) {
        val mid = (low + high).ushr(1) // safe from overflows
        val midVal = get(mid)
        val cmp = comparison(midVal)

        when {
            cmp < 0 -> low = mid + 1
            cmp > 0 -> high = mid - 1
            else -> return mid // key found
        }
    }
    return -(low + 1)  // key not found
}