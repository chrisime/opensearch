package com.github.chrisime.search.model

data class SearchIndexCoordinates(
    val searchAlias: SearchAlias,
    val searchIndex: SearchIndex,
    val indexPattern: String? = null
)
