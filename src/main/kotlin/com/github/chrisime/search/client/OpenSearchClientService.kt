package com.github.chrisime.search.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.chrisime.search.model.BulkResult
import com.github.chrisime.search.model.IndexResult
import com.github.chrisime.search.model.MappingResult
import com.github.chrisime.search.model.SearchBulkResponseItem
import com.github.chrisime.search.model.SearchDocument
import com.github.chrisime.search.model.SearchIndex
import com.github.chrisime.search.model.SearchIndexCoordinates
import com.github.chrisime.search.model.SearchResult
import com.github.chrisime.search.provider.OpenSearchClientProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.json.spi.JsonProvider
import org.opensearch.client.ResponseException
import org.opensearch.client.json.jackson.JacksonJsonpMapper
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

class OpenSearchClientService(clientProvider: OpenSearchClientProvider) {

    private val client: OpenSearchClient =  clientProvider.getClient()
    private val mapper: JacksonJsonpMapper = client._transport().jsonpMapper() as JacksonJsonpMapper

    private val jsonProvider: JsonProvider = mapper.jsonProvider()
    private val objectMapper: ObjectMapper = mapper.objectMapper()

    fun createIndex(indexCoordinates: SearchIndexCoordinates, mapping: String): IndexResult {
        val indexConfig = this::class.java.getResourceAsStream(mapping)
            ?: throw IllegalArgumentException("indexConfig.json not found in resources")

        val parser = jsonProvider.createParser(indexConfig)

        val request = CreateIndexRequest.Builder()
            .index(indexCoordinates.searchIndex.indexName)
            .aliases(indexCoordinates.searchAlias.aliasName, Alias.Builder().build())
            .mappings(TypeMapping._DESERIALIZER.deserialize(parser, mapper))
            .build()


        return runCatching {
            val response = client.indices().create(request)

            IndexResult.Success(
                index = response.index(),
                acknowledged = response.acknowledged(),
                shardsAcknowledged = response.shardsAcknowledged()
            )
        }.getOrElse { e ->
            when (e) {
                is ResponseException -> IndexResult.Failure(
                    error = e.response.toString(),
                    exception = e
                )

                is OpenSearchException -> IndexResult.Failure(
                    error = e.error().reason() ?: "OpenSearch error",
                    metadata = e.error().metadata(),
                    exception = e
                )

                is IOException -> IndexResult.Failure(
                    error = e.message ?: "IO error",
                    exception = e
                )

                else -> IndexResult.Failure(
                    error = e.message ?: "Unknown error",
                    exception = e
                )
            }
        }
    }

    fun <T> search(
        searchIndex: SearchIndex,
        size: Int = 1000,
        from: Int = 0,
        queryBuilder: (Query.Builder.() -> Unit)? = null,
        clazz: Class<T>
    ): SearchResult<T> {
        val query = queryBuilder?.let { builder ->
            Query.Builder().apply(builder).build()
        }

        val searchRequest = SearchRequest.Builder()
            .index(searchIndex.indexName)
            .size(size)
            .from(from)
            .query(query)
            .build()

        val response = client.search(searchRequest, clazz)
        val documents = response.hits().hits().mapNotNull { it.source() }

        return SearchResult(
            documents = documents,
            totalHits = response.hits().total()?.value() ?: 0,
            score = response.hits().maxScore() ?: 0.0,
            tookMs = response.took()
        )
    }

    inline fun <reified T> search(
        searchIndex: SearchIndex,
        size: Int = 1000,
        from: Int = 0,
        noinline queryBuilder: (Query.Builder.() -> Unit)? = null
    ): SearchResult<T> = search(searchIndex, size, from, queryBuilder, T::class.java)

    fun <T> bulkRequest(
        searchDocuments: List<SearchDocument<T>>,
        searchIndex: SearchIndex,
        operation: (List<SearchDocument<T>>, SearchIndex) -> List<SearchBulkResponseItem>
    ): BulkResult<T> {

        return runCatching {
            val results = operation(searchDocuments, searchIndex)

            BulkResult.Success<T>(
                numOfDocuments = searchDocuments.size,
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

    fun <T> bulkIndexOperation(
        queries: List<SearchDocument<T>>,
        searchIndex: SearchIndex
    ): List<SearchBulkResponseItem> {
        log.info { "Starting bulk request with ${queries.size} documents" }

        val bulkRequest = BulkRequest.Builder().apply {
            queries.forEach { query ->
                operations { bulkOp ->
                    bulkOp.index { idxOp ->
                        idxOp.index(searchIndex.indexName)
                        idxOp.id(query.id)
                        idxOp.document(query.document)
                    }

                }
            }
        }.build()

        log.info { "Sending bulk request to OpenSearch" }
        val response = client.bulk(bulkRequest)
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
                primaryTerm = responseItem.primaryTerm(),
                forcedRefresh = responseItem.forcedRefresh()
            )
        }
    }

    fun countDocuments(
        searchIndex: SearchIndex,
        queryBuilder: (Query.Builder.() -> Unit)? = null
    ): Long {
        val countRequest = CountRequest.Builder()
            .index(searchIndex.indexName)
            .apply {
                queryBuilder?.let { builder ->
                    query(Query.Builder().apply(builder).build())
                }
            }
            .build()

        val response = client.count(countRequest)

        return response.count()
    }

    fun getMapping(indexName: String): MappingResult {
        val request = GetMappingRequest.Builder().index(indexName).build()
        return runCatching {
            val response = client.indices().getMapping(request)

            MappingResult.Success(
                mapping = response.result().map { (_, indexMappingData) ->
                    val mappingJson = indexMappingData.mappings().toJsonString()

                   objectMapper.readTree(mappingJson).toPrettyString()
                }
            )
        }.getOrElse { e ->
            when (e) {
                is OpenSearchException -> MappingResult.Failure(
                    error = e.error().reason() ?: "OpenSearch error",
                    metadata = e.error().metadata(),
                    exception = e
                )

                is IOException -> MappingResult.Failure(
                    error = e.message ?: "IO error",
                    exception = e
                )

                else -> MappingResult.Failure(
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
