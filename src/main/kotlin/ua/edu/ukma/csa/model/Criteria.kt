package ua.edu.ukma.csa.model

import kotlinx.serialization.Serializable

@Serializable
data class Criteria(
    val name: String? = null,
    val fromPrice: Double? = null,
    val toPrice: Double? = null,
    val inGroups: Set<String> = emptySet()
)
