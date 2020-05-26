package ua.edu.ukma.csa.model

import java.util.*

data class Product(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    var count: Int = 0,
    var price: Double
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Product

        if (id != other.id) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}

