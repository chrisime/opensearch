package com.dertouristik.search.model

sealed class IndexResult {
    data class Success(
        val index: String,
        val acknowledged: Boolean?,
        val shardsAcknowledged: Boolean,
    ) : IndexResult()

    data class Error<T: Throwable>(
        val error: String,
        val metadata: Map<String, Any>? = null,
        val exception: T,
    ) : IndexResult()
}
