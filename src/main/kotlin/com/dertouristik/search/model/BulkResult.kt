package com.dertouristik.search.model

sealed class BulkResult<T> {
    data class Success<T>(
        val numOfDocuments: Int,
        val results: List<SearchBulkResponseItem>
    ) : BulkResult<T>()

    data class Failure<T>(
        val documentSize: Int,
        val error: String,
        val warnings: List<String>,
        val exception: Exception
    ) : BulkResult<T>()
}
