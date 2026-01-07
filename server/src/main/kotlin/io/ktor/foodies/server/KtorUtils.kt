package io.ktor.foodies.server

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.html.TagConsumer
import kotlinx.html.stream.appendHTML

suspend fun ApplicationCall.respondHtmxFragment(
    block: TagConsumer<StringBuilder>.() -> Unit
) = respondText(buildString { appendHTML().block() }, ContentType.Text.Html)
