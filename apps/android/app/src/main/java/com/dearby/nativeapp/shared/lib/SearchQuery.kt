package com.dearby.nativeapp.shared.lib

/** Every whitespace-separated term must appear in some field; case and surrounding spaces are ignored. */
fun matchesSearch(query: String, fields: List<String>): Boolean {
    val terms = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val values = fields.map { it.lowercase() }
    return terms.all { term -> values.any { it.contains(term) } }
}
