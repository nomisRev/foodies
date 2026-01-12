package io.ktor.foodies.server

import io.ktor.server.plugins.BadRequestException
import io.ktor.util.internal.initCauseBridge
import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.ExperimentalCoroutinesApi

interface ValidateSyntax {
    fun <A : Any> A.validate(predicate: (A) -> Boolean, errorMessage: A.() -> String): A
}

@OptIn(ExperimentalCoroutinesApi::class)
class ValidationException(override val message: String, val reasons: List<String>) :
    BadRequestException(message),
    CopyableThrowable<ValidationException> {

    override fun createCopy(): ValidationException =
        ValidationException(message, reasons).also {
            it.initCauseBridge(this)
        }
}

fun <A> validate(
    errorsToMessage: (List<String>) -> String = { it.joinToString("; ") },
    block: ValidateSyntax.() -> A
): A {
    val errors = mutableListOf<String>()
    val syntax = object : ValidateSyntax {
        override fun <A : Any> A.validate(predicate: (A) -> Boolean, errorMessage: A.() -> String): A =
            if (!predicate(this)) this.also { errors.add(errorMessage()) } else this
    }
    val a = block(syntax)
    return if (errors.isNotEmpty()) throw ValidationException(errorsToMessage(errors), errors) else a
}
