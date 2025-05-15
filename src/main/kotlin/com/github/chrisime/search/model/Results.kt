package com.github.chrisime.search.model

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

sealed class IndexResult {
    data class Success(
        val index: String,
        val acknowledged: Boolean?,
        val shardsAcknowledged: Boolean,
    ) : IndexResult()

    data class Failure<T: Throwable>(
        val error: String,
        val metadata: Map<String, Any>? = null,
        val exception: T,
    ) : IndexResult()
}

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
