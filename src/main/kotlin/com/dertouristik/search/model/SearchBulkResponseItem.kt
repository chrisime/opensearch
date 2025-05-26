package com.dertouristik.search.model

data class SearchBulkResponseItem(
    val id: String?,
    val index: String,
    val version: Long?,
    val seqNo: Long?,
    val primaryTerm: Long?
)
