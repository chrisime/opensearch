package com.dertouristik.search.model

data class SearchIndexCoordinates(
    val searchAlias: SearchAlias,
    val searchIndex: SearchIndex,
    val indexPattern: String? = null
)
