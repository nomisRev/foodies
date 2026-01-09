package io.ktor.foodies.server

import io.ktor.http.Parameters
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.plugins.ParameterConversionException
import io.ktor.util.converters.DefaultConversionService
import io.ktor.util.reflect.TypeInfo
import io.ktor.util.reflect.typeInfo
import kotlinx.serialization.serializer
import kotlin.reflect.KProperty

/**
 * This variant of [io.ktor.server.util.getValue] that supports nullable types.
 */
inline operator fun <reified R> Parameters.getValue(thisRef: Any?, property: KProperty<*>): R {
    val typeInfo = typeInfo<R>()
    return if (typeInfo.kotlinType?.isMarkedNullable == true && get(property.name) == null) null as R
    else getOrFailImpl(property.name, typeInfo)
}

@PublishedApi
internal fun <R> Parameters.getOrFailImpl(name: String, typeInfo: TypeInfo): R {
    val values = getAll(name) ?: throw MissingRequestParameterException(name)
    return try {
        @Suppress("UNCHECKED_CAST")
        DefaultConversionService.fromValues(values, typeInfo) as R
    } catch (cause: Exception) {
        throw ParameterConversionException(name, typeInfo.type.simpleName ?: typeInfo.type.toString(), cause)
    }
}