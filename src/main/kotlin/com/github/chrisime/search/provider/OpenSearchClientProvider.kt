package com.github.chrisime.search.provider

import org.opensearch.client.opensearch.OpenSearchClient

interface OpenSearchClientProvider {
    fun getClient(): OpenSearchClient
}
