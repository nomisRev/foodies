package io.ktor.foodies.server

import io.ktor.server.plugins.BadRequestException

interface ValidateSyntax {
    fun <A : Any> A.validate(predicate: (A) -> Boolean, errorMessage: A.() -> String): A
}

class ValidationError(
    message: String,
    val reasons: List<String>
) : BadRequestException(message)

fun <A> validate(
    errorsToMessage: (List<String>) -> String = { it.joinToString("; ") },
    block: ValidateSyntax.() -> A
): A {
    val errors = mutableListOf<String>()
    val syntax = object : ValidateSyntax {
        override fun <A: Any > A.validate(predicate: (A) -> Boolean, errorMessage: A.() -> String): A =
            if (!predicate(this)) this.also { errors.add(errorMessage()) } else this
    }
    val a = block(syntax)
    return if (errors.isNotEmpty()) throw ValidationError(errorsToMessage(errors), errors) else a
}
