package com.github.chrisime.search.configuration

data class OpenSearchConfig(
    val scheme: String,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val useSsl: Boolean = false,
    val useBasicAuth: Boolean = true,
)
