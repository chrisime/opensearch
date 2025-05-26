package com.dertouristik.portfolio.searchclient

import com.dertouristik.search.client.OpenSearchClient
import com.dertouristik.search.configuration.OpenSearchConfig
import com.dertouristik.search.model.BulkResult
import com.dertouristik.search.model.IndexResult
import com.dertouristik.search.model.MappingResult
import com.dertouristik.search.model.SearchAlias
import com.dertouristik.search.model.SearchDocument
import com.dertouristik.search.model.SearchIndex
import com.dertouristik.search.model.SearchIndexCoordinates
import org.awaitility.Awaitility.await
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
import java.time.Duration
import java.util.UUID

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenSearchTest {

    private lateinit var searchClient: OpenSearchClient

    @BeforeAll
    fun setup() {
        // Warten bis Container bereit ist
        opensearchContainer.start()

        // Konfiguration mit Container-Werten erstellen
        val config = OpenSearchConfig(
            scheme = "http",
            host = opensearchContainer.host,
            port = opensearchContainer.getMappedPort(9200),
            username = "", // Keine Authentifizierung da Security disabled
            password = ""
        )

        searchClient = OpenSearchClient(config)
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

        val results = searchClient.upsertWithResults(
            searchDocuments = documents,
            searchIndex = SearchIndex("my-index2"),
        ) { queries, coordinates ->
            searchClient.bulkRequest(queries, coordinates)
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

        searchClient.upsertWithResults(
            searchDocuments = documents,
            searchIndex = SearchIndex("my-index3"),
        ) { queries, coordinates ->
            searchClient.bulkRequest(queries, coordinates)
        }

        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(200))
            .until {
                try {
                    searchClient.countDocuments(SearchIndex("my-index3")) >= 10L
                } catch (_: Exception) {
                    false
                }
            }

        val totalCount = searchClient.countDocuments(SearchIndex("my-index3"))
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
        Assertions.assertEquals(10L, totalCount, "Total count of documents should be 10")
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

        searchClient.upsertWithResults(
            searchDocuments = documents,
            searchIndex = SearchIndex("my-index4"),
        ) { queries, coordinates ->
            searchClient.bulkRequest(queries, coordinates)
        }

        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(200))
            .until {
                try {
                    searchClient.countDocuments(SearchIndex("my-index4")) >= 10L
                } catch (_: Exception) {
                    false
                }
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
