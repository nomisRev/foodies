package com.foodies.e2e.config

object TestConfig {
    val baseUrl: String = System.getenv("BASE_URL") ?: "http://localhost:8080"
}
