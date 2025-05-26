package com.dertouristik.search.factory

import com.dertouristik.search.configuration.OpenSearchConfig
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.ssl.SSLContextBuilder
import org.opensearch.client.RestClient
import org.opensearch.client.json.jackson.JacksonJsonpMapper
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.transport.rest_client.RestClientTransport
import javax.net.ssl.SSLContext

object OpenSearchClientFactory {

    fun createClient(config: OpenSearchConfig): OpenSearchClient {
        val restClientBuilder = RestClient.builder(
            HttpHost(config.host, config.port, config.scheme)
        )

        if (config.useSsl) {
            val sslContext: SSLContext = SSLContextBuilder.create()
                .loadTrustMaterial(null) { _, _ -> true } // Trust all certificates - for dev only!
                .build()

            restClientBuilder.setHttpClientConfigCallback { httpClientBuilder ->
                httpClientBuilder.setSSLContext(sslContext)
            }
        }


        if (config.useBasicAuth) {
            val credentialsProvider = BasicCredentialsProvider().apply {
                setCredentials(
                    AuthScope.ANY,
                    UsernamePasswordCredentials(config.username, config.password)
                )
            }

            restClientBuilder.setHttpClientConfigCallback { httpClientBuilder ->
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider).apply {
                    if (config.useSsl) setSSLContext(
                        SSLContextBuilder.create().loadTrustMaterial(null) { _, _ -> true }.build()
                    )
                }
            }
        }

        val objectMapper = jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
            .configure(SerializationFeature.INDENT_OUTPUT, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)


        val transport = RestClientTransport(
            restClientBuilder.build(),
            JacksonJsonpMapper(objectMapper)
        )

        return OpenSearchClient(transport)
    }

}
