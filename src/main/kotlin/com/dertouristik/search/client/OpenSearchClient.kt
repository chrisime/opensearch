package com.dertouristik.search.client

import com.dertouristik.search.configuration.OpenSearchConfig
import com.dertouristik.search.factory.OpenSearchClientFactory
import com.dertouristik.search.model.BulkResult
import com.dertouristik.search.model.SearchDocument
import com.dertouristik.search.model.IndexResult
import com.dertouristik.search.model.MappingResult
import com.dertouristik.search.model.SearchBulkResponseItem
import com.dertouristik.search.model.SearchIndex
import com.dertouristik.search.model.SearchIndexCoordinates
import com.dertouristik.search.model.SearchResult
import io.github.oshai.kotlinlogging.KotlinLogging
import org.opensearch.client.ResponseException
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.OpenSearchException
import org.opensearch.client.opensearch._types.mapping.TypeMapping
import org.opensearch.client.opensearch._types.query_dsl.Query
import org.opensearch.client.opensearch.core.BulkRequest
import org.opensearch.client.opensearch.core.CountRequest
import org.opensearch.client.opensearch.core.SearchRequest
import org.opensearch.client.opensearch.indices.Alias
import org.opensearch.client.opensearch.indices.CreateIndexRequest
import org.opensearch.client.opensearch.indices.GetMappingRequest
import java.io.IOException


class OpenSearchClient(config: OpenSearchConfig) {

    private val openSearchClient: OpenSearchClient by lazy { OpenSearchClientFactory.createClient(config) }

    fun createIndex(indexCoordinates: SearchIndexCoordinates, mapping: String): IndexResult {
        val indexConfig = this::class.java.getResourceAsStream(mapping)
            ?: throw IllegalArgumentException("indexConfig.json not found in resources")

        val mapper = openSearchClient._transport().jsonpMapper()
        val parser = mapper.jsonProvider().createParser(indexConfig)

        val request = CreateIndexRequest.Builder()
            .index(indexCoordinates.searchIndex.indexName)
            .aliases(indexCoordinates.searchAlias.aliasName, Alias.Builder().build())
            .mappings(TypeMapping._DESERIALIZER.deserialize(parser, mapper))
            .build()


        return runCatching {
            val response = openSearchClient.indices().create(request)

            IndexResult.Success(
                index = response.index(),
                acknowledged = response.acknowledged(),
                shardsAcknowledged = response.shardsAcknowledged()
            )
        }.getOrElse { e ->
            when (e) {
                is ResponseException -> IndexResult.Error(
                    error = e.response.toString(),
                    exception = e
                )

                is OpenSearchException -> IndexResult.Error(
                    error = e.error().reason() ?: "OpenSearch error",
                    metadata = e.error().metadata(),
                    exception = e
                )

                is IOException -> IndexResult.Error(
                    error = e.message ?: "IO error",
                    exception = e
                )

                else -> IndexResult.Error(
                    error = e.message ?: "Unknown error",
                    exception = e
                )
            }
        }
    }

    fun <T> search(
        indexName: String,
        size: Int = 1000,
        from: Int = 0,
        queryBuilder: (Query.Builder.() -> Unit)? = null,
        clazz: Class<T>
    ): SearchResult<T> {
        val query = queryBuilder?.let { builder ->
            Query.Builder().apply(builder).build()
        }

        val searchRequest = SearchRequest.Builder()
            .index(indexName)
            .size(size)
            .from(from)
            .query(query)
            .build()

        val response = openSearchClient.search(searchRequest, clazz)
        val documents = response.hits().hits().mapNotNull { it.source() }

        return SearchResult(
            documents = documents,
            totalHits = response.hits().total()?.value() ?: 0,
            score = response.hits().maxScore() ?: 0.0,
            tookMs = response.took()
        )
    }

    inline fun <reified T> search(
        indexName: String,
        size: Int = 1000,
        from: Int = 0,
        noinline queryBuilder: (Query.Builder.() -> Unit)? = null,
    ): SearchResult<T> = search(indexName, size, from, queryBuilder, T::class.java)

    fun <T> upsertWithResults(
        searchDocuments: List<SearchDocument<T>>,
        searchIndex: SearchIndex,
        operation: (List<SearchDocument<T>>, SearchIndex) -> List<SearchBulkResponseItem>
    ): BulkResult<T> {

        return runCatching {
            val results = operation(searchDocuments, searchIndex)

            BulkResult.Success<T>(
                batchSize = searchDocuments.size,
                results = results
            )
        }.getOrElse { e ->
            when (e) {
                is ResponseException -> BulkResult.Failure(
                    documentSize = searchDocuments.size,
                    warnings = e.response.warnings,
                    error = e.response?.toString() ?: "Response error",
                    exception = e
                )

                is OpenSearchException -> BulkResult.Failure(
                    documentSize = searchDocuments.size,
                    error = e.error().reason() ?: "OpenSearch error",
                    warnings = emptyList(),
                    exception = e
                )

                is IOException -> BulkResult.Failure(
                    documentSize = searchDocuments.size,
                    error = e.message ?: "IO error",
                    warnings = emptyList(),
                    exception = e
                )

                else -> BulkResult.Failure(
                    documentSize = searchDocuments.size,
                    error = e.message ?: "Unknown error",
                    warnings = emptyList(),
                    exception = e as Exception
                )
            }
        }
    }

    fun <T> bulkRequest(
        queries: List<SearchDocument<T>>,
        searchIndex: SearchIndex
    ): List<SearchBulkResponseItem> {
        log.info { "Starting bulk request with ${queries.size} documents" }

        val bulkRequest = BulkRequest.Builder().apply {
            queries.forEach { query ->
                operations { bulkOp ->
                    bulkOp.index { idxOp ->
                        idxOp.index(searchIndex.indexName)
                        query.id?.let { idxOp.id(it) }
                        idxOp.document(query.document)
                    }

                }
            }
        }.build()

        log.info { "Sending bulk request to OpenSearch" }
        val response = openSearchClient.bulk(bulkRequest)
        log.info { "Received bulk response from OpenSearch" }

        val items = response.items()
        log.info { "Bulk operation returned ${items.size} items" }

        if (response.errors()) {
            log.warn { "Bulk operation had some errors for index $searchIndex" }
            items.forEach { item ->
                item.error()?.let { error ->
                    log.warn { "Bulk error for index ${item.index()}, document ${item.id()}: ${error.reason()}" }
                }
            }
        }

        return items.map { responseItem ->
            SearchBulkResponseItem(
                id = responseItem.id(),
                index = responseItem.index(),
                version = responseItem.version(),
                seqNo = responseItem.seqNo(),
                primaryTerm = responseItem.primaryTerm()
            )
        }
    }

    fun countDocuments(
        indexName: String,
        queryBuilder: (Query.Builder.() -> Unit)? = null
    ): Long {
        val countRequest = CountRequest.Builder()
            .index(indexName)
            .apply {
                queryBuilder?.let { builder ->
                    val query = Query.Builder().apply(builder).build()
                    query(query)
                }
            }
            .build()

        val response = openSearchClient.count(countRequest)

        return response.count()
    }

    fun getMapping(indexName: String): MappingResult {
        val request = GetMappingRequest.Builder().index(indexName).build()
        return runCatching {
            val response = openSearchClient.indices().getMapping(request)

            MappingResult.Success(
                mapping = response.result().map { (key, value) ->
                    "$key -> ${value.mappings().toJsonString()}"
                }
            )
        }.getOrElse { e ->
            when (e) {
                is OpenSearchException -> MappingResult.Error(
                    error = e.error().reason() ?: "OpenSearch error",
                    metadata = e.error().metadata(),
                    exception = e
                )

                is IOException -> MappingResult.Error(
                    error = e.message ?: "IO error",
                    exception = e
                )

                else -> MappingResult.Error(
                    error = e.message ?: "Unknown error",
                    exception = e as Exception
                )
            }
        }
    }

    private companion object {
        private val log = KotlinLogging.logger { }
    }

}
