package com.github.chrisime.search.model

data class SearchResult<T>(
    val documents: List<T>,
    val totalHits: Long,
    val score: Double,
    val tookMs: Long?
)
