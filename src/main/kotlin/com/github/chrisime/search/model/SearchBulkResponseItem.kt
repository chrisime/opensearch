package com.github.chrisime.search.model

data class SearchBulkResponseItem(
    val id: String?,
    val index: String,
    val version: Long?,
    val seqNo: Long?,
    val primaryTerm: Long?,
    val forcedRefresh: Boolean?
)
