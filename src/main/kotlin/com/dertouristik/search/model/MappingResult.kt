package com.dertouristik.search.model

sealed class MappingResult {
    data class Success(
        val mapping: List<String>,
    ) : MappingResult()

    data class Failure<T : Throwable>(
        val error: String,
        val metadata: Map<String, Any>? = null,
        val exception: T,
    ) : MappingResult()
}
