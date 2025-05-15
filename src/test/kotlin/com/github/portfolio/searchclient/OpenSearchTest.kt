package com.github.portfolio.searchclient

import com.github.chrisime.search.client.OpenSearchClientService
import com.github.chrisime.search.configuration.OpenSearchConfig
import com.github.chrisime.search.model.BulkResult
import com.github.chrisime.search.model.IndexResult
import com.github.chrisime.search.model.MappingResult
import com.github.chrisime.search.model.SearchAlias
import com.github.chrisime.search.model.SearchDocument
import com.github.chrisime.search.model.SearchIndex
import com.github.chrisime.search.model.SearchIndexCoordinates
import com.github.chrisime.search.provider.DefaultOpenSearchClientProvider
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.awaitility.kotlin.withAlias
import org.awaitility.kotlin.withPollInterval
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.opensearch.client.opensearch._types.FieldValue
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenSearchTest {

    private lateinit var searchClient: OpenSearchClientService

    @BeforeAll
    fun setup() {
        opensearchContainer.start()

        val config = OpenSearchConfig(
            scheme = "http",
            host = opensearchContainer.host,
            port = opensearchContainer.getMappedPort(9200),
        )

        val openSearchClientProvider = DefaultOpenSearchClientProvider(config)
        searchClient = OpenSearchClientService(openSearchClientProvider)
    }

    @Test
    fun `should create an index with alias`() {
        val indexResult = searchClient.createIndex(
            SearchIndexCoordinates(
                SearchAlias("alias-index"),
                SearchIndex("my-index1")
            ),
            "/search-config.json"
        )

        when (indexResult) {
            is IndexResult.Success -> {
                Assertions.assertEquals("my-index1", indexResult.index)
            }

            else -> {
                Assertions.fail("no index created")
            }
        }

        val documents = (1..10).map {
            SearchDocument(
                document = MyDocument(
                    uid = UUID.randomUUID().toString(),
                    name = "Random Name $it",
                    value = it
                ),
                id = UUID.randomUUID().toString()
            )
        }

        searchClient.bulkRequest(
            searchDocuments = documents,
            searchIndex = SearchIndex("my-index1"),
        ) { queries, coordinates ->
            searchClient.bulkIndexOperation(queries, coordinates)
        }

        val mappingResult = searchClient.getMapping("my-index1")

        when (mappingResult) {
            is MappingResult.Success -> {
                Assertions.assertTrue(mappingResult.mapping.isNotEmpty())
            }

            else -> {
                Assertions.fail("no index created")
            }
        }
    }

    @Test
    fun `should bulk upsert documents`() {
        val documents = (1..10).map {
            SearchDocument(
                document = MyDocument(
                    uid = UUID.randomUUID().toString(),
                    name = "Random Name $it",
                    value = it
                ),
                id = UUID.randomUUID().toString()
            )
        }

        val results = searchClient.bulkRequest(
            searchDocuments = documents,
            searchIndex = SearchIndex("my-index2"),
        ) { queries, coordinates ->
            searchClient.bulkIndexOperation(queries, coordinates)
        }

        // Results verarbeiten
        when (results) {
            is BulkResult.Success -> {
                Assertions.assertEquals(10, results.numOfDocuments)
            }

            is BulkResult.Failure -> {
                Assertions.fail("there should be 10 documents indexed, but got error: ${results.error}")
            }
        }
    }

    @Test
    @Timeout(15000L)
    fun `should count and search documents`() {
        val documents = (1..10).map {
            SearchDocument(
                document = MyDocument(
                    uid = UUID.randomUUID().toString(),
                    name = "Name $it",
                    value = it
                ),
                id = UUID.randomUUID().toString()
            )
        }

        searchClient.bulkRequest(
            searchDocuments = documents,
            searchIndex = SearchIndex("my-index3"),
        ) { queries, coordinates ->
            searchClient.bulkIndexOperation(queries, coordinates)
        }

        await.withAlias("documents available").atMost(10.seconds)
            .withPollInterval(200.milliseconds)
            .untilCallTo { searchClient.countDocuments(SearchIndex("my-index3")) }.matches { cnt ->
                cnt!! >= 10L
            }

        val counts = searchClient.countDocuments(SearchIndex("my-index3")) {
            bool { builder ->
                builder.must { m ->
                    m.term { t ->
                        t.field("value").value(FieldValue.of(1))
                    }
                }.must { builder ->
                    builder.matchPhrase {
                        it.field("name").query("Name 1")
                    }
                }
            }
        }

        Assertions.assertEquals(1L, counts, "No documents found with name 'Name 1' and value 1")
    }

    @Test
    fun `should search documents with specific criteria`() {
        val documents = (1..10).map {
            SearchDocument(
                document = MyDocument(
                    uid = UUID.randomUUID().toString(),
                    name = "Random Name $it",
                    value = it
                ),
                id = UUID.randomUUID().toString()
            )
        }

        searchClient.bulkRequest(
            searchDocuments = documents,
            searchIndex = SearchIndex("my-index4"),
        ) { queries, coordinates ->
            searchClient.bulkIndexOperation(queries, coordinates)
        }

        await.withAlias("documents available").atMost(10.seconds)
            .withPollInterval(200.milliseconds)
            .untilCallTo { searchClient.countDocuments(SearchIndex("my-index4")) } matches { cnt ->
            cnt!! >= 10L
        }

        val search = searchClient.search<MyDocument>(
            searchIndex = SearchIndex("my-index4"),
            queryBuilder = {
                bool { builder ->
                    builder.must { m ->
                        m.term { t ->
                            t.field("value").value(FieldValue.of(1))
                        }
                    }
                }
            }
        )

        Assertions.assertEquals(1, search.documents.size, "No documents found in search for value 1")
    }

    @Test
    fun `should update documents with same id`() {
        val documents = (1..10).map {
            SearchDocument(
                document = MyDocument(
                    uid = "UID1 uid-$it",
                    name = "Random Name1 $it",
                    value = it
                ),
                id = "$it"
            )
        }

        searchClient.bulkRequest(
            searchDocuments = documents,
            searchIndex = SearchIndex("my-index5"),
        ) { queries, coordinates ->
            searchClient.bulkIndexOperation(queries, coordinates)
        }

        val documents2Update = (5..10).map {
            SearchDocument(
                document = MyDocument(
                    uid = "UID2 uid-$it",
                    name = "Random Name2 $it",
                    value = it
                ),
                id = "$it"
            )
        }

        searchClient.bulkRequest(
            searchDocuments = documents2Update,
            searchIndex = SearchIndex("my-index5"),
        ) { queries, coordinates ->
            searchClient.bulkIndexOperation(queries, coordinates)
        }

        await.withAlias("documents available").atMost(10.seconds)
            .withPollInterval(200.milliseconds)
            .untilCallTo { searchClient.countDocuments(SearchIndex("my-index5")) } matches { cnt ->
            cnt!! >= 10L
        }

        val documentsInIndex = searchClient.search<MyDocument>(SearchIndex("my-index5"))

        Assertions.assertEquals(10, documentsInIndex.documents.size, "Expected 10 documents in index")
        documentsInIndex.documents.slice(5..9).forEachIndexed { idx, doc ->
            Assertions.assertEquals("UID2 uid-${idx + 6}", doc.uid)
            Assertions.assertEquals("Random Name2 ${idx + 6}", doc.name)
        }
    }

    companion object {
        data class MyDocument(val uid: String, val name: String, val value: Int)

        @Container
        @JvmStatic
        val opensearchContainer: GenericContainer<*> = GenericContainer(
            DockerImageName.parse("opensearchproject/opensearch:2.19.2")
        ).apply {
            withExposedPorts(9200, 9600)
            withEnv("OPENSEARCH_INITIAL_ADMIN_PASSWORD", "admin")
            withEnv("DISABLE_SECURITY_PLUGIN", "true")
            withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
            withEnv("discovery.type", "single-node")
            waitingFor(
                Wait.forHttp("/_cluster/health")
                    .forStatusCode(200)
                    .forPort(9200)
            )
        }
    }

}
