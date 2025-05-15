package com.github.chrisime.search.model

data class SearchDocument<T>(
    val document: T,
    val id: String? = null
)
