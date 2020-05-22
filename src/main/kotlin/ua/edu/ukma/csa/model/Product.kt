package ua.edu.ukma.csa.model

import java.util.*

data class Product(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val groups: List<String> = emptyList(),
    var count: Int = 0,
    var price: Double
)
