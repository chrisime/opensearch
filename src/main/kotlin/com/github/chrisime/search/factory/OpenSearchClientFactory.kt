package com.github.chrisime.search.factory

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.chrisime.search.configuration.OpenSearchConfig
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

    val objectMapper: ObjectMapper = jacksonMapperBuilder()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
        .configure(SerializationFeature.INDENT_OUTPUT, false)
        .addModule(JavaTimeModule())
        .build()
        .registerKotlinModule {
            this.configure(KotlinFeature.UseJavaDurationConversion, true)
        }
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)

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

        val transport = RestClientTransport(
            restClientBuilder.build(),
            JacksonJsonpMapper(objectMapper)
        )

        return OpenSearchClient(transport)
    }

}
