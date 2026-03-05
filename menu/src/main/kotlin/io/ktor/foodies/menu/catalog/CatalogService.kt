package io.ktor.foodies.menu.catalog

import io.ktor.foodies.menu.MenuItem
import io.ktor.foodies.menu.persistence.MenuRepository

interface CatalogService {
    fun list(offset: Int? = null, limit: Int? = null): List<MenuItem>
    fun get(id: Long): MenuItem?
}

class CatalogServiceImpl(private val repository: MenuRepository) : CatalogService {
    override fun list(offset: Int?, limit: Int?): List<MenuItem> {
        val safeOffset = (offset ?: DEFAULT_OFFSET).coerceAtLeast(0)
        val safeLimit = (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        return repository.list(safeOffset, safeLimit)
    }

    override fun get(id: Long): MenuItem? = repository.findById(id)

    private companion object {
        const val DEFAULT_OFFSET = 0
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 50
    }
}
