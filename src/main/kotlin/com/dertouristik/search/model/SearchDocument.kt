package com.dertouristik.search.model

data class SearchDocument<T>(
    val document: T,
    val id: String?
)
