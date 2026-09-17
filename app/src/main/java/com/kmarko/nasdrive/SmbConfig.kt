package com.kmarko.nasdrive

data class SmbConfig(
    val host: String,
    val port: Int = 445,
    val shareName: String,
    val username: String,
    val password: String,
    val domain: String = ""
)
