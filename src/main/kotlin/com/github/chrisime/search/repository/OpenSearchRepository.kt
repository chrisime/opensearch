package com.github.chrisime.search.repository

import com.github.chrisime.search.client.OpenSearchClientService
import com.github.chrisime.search.model.BulkResult
import com.github.chrisime.search.model.SearchBulkResponseItem
import com.github.chrisime.search.model.SearchDocument
import com.github.chrisime.search.model.SearchIndex
import com.github.chrisime.search.model.SearchResult
import com.github.chrisime.search.provider.OpenSearchClientProvider
import org.opensearch.client.opensearch._types.query_dsl.Query

abstract class OpenSearchRepository<T>(clientProvider: OpenSearchClientProvider) {

    private val openSearchClientService: OpenSearchClientService = OpenSearchClientService(clientProvider)

    protected abstract val documentClass: Class<T>

    protected abstract val searchIndex: SearchIndex

    fun insertDocuments(index: SearchIndex = searchIndex, documents: List<T>): List<SearchBulkResponseItem> {
        val searchDocuments = documents.mapIndexed { idx, document ->
            SearchDocument(
                id = generateDocumentId(document, idx),
                document = document
            )
        }

        val result = openSearchClientService.bulkRequest(
            searchDocuments = searchDocuments,
            searchIndex = index,
            operation = { docs, idx -> openSearchClientService.bulkIndexOperation(docs, idx) }
        )

        return when (result) {
            is BulkResult.Success -> result.results
            is BulkResult.Failure -> throw RuntimeException("Bulk insert failed: ${result.error}", result.exception)
        }
    }

    fun countDocuments(index: SearchIndex = searchIndex, queryBuilder: (Query.Builder.() -> Unit)? = null): Long {
        return openSearchClientService.countDocuments(index, queryBuilder)
    }

    fun searchDocuments(
        index: SearchIndex = searchIndex,
        from: Int = 0,
        size: Int = 1000,
        queryBuilder: (Query.Builder.() -> Unit)? = null
    ): SearchResult<T> = openSearchClientService.search(
        searchIndex = index,
        size = size,
        from = from,
        queryBuilder = queryBuilder,
        clazz = documentClass
    )

    protected open fun generateDocumentId(document: T, index: Int): String = "${document.hashCode()}_$index"

}
