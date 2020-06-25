package ua.edu.ukma.csa.network.http

import java.net.URI

fun splitQuery(url: URI): Map<String, List<String?>> =
    if (url.query.isNullOrBlank()) emptyMap()
    else url.query.splitToSequence("&")
        .map { splitQueryParameter(it) }
        .groupBy({ it.first }, { it.second })

fun splitQueryParameter(it: String): Pair<String, String?> {
    val idx = it.indexOf("=")
    val key = if (idx > 0) it.substring(0, idx) else it
    val value = if (idx > 0 && it.length > idx + 1) it.substring(idx + 1) else null
    return key to value
}