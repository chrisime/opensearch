package com.github.chrisime.search.provider

import com.github.chrisime.search.configuration.OpenSearchConfig
import com.github.chrisime.search.factory.OpenSearchClientFactory
import org.opensearch.client.opensearch.OpenSearchClient

class DefaultOpenSearchClientProvider(private val config: OpenSearchConfig) : OpenSearchClientProvider {
    private val openSearchClient: OpenSearchClient by lazy { OpenSearchClientFactory.createClient(config) }

    override fun getClient(): OpenSearchClient = openSearchClient
}
