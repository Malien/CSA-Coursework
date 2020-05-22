package ua.edu.ukma.csa

import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class Product(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val groups: List<String> = emptyList(),
    var count: Int = 0,
    var price: Double
)

val model = ConcurrentHashMap<UUID, Product>(100)
