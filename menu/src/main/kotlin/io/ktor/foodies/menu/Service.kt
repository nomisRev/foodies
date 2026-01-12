package io.ktor.foodies.menu

interface MenuService {
    fun list(offset: Int? = null, limit: Int? = null): List<MenuItem>
    fun get(id: Long): MenuItem?
    fun create(request: CreateMenuItemRequest): MenuItem
    fun update(id: Long, request: UpdateMenuItemRequest): MenuItem?
    fun delete(id: Long): Boolean
    fun search(query: String): List<MenuItem>
}

class MenuServiceImpl(private val repository: MenuRepository) : MenuService {
    override fun list(offset: Int?, limit: Int?): List<MenuItem> {
        val safeOffset = (offset ?: DEFAULT_OFFSET).coerceAtLeast(0)
        val safeLimit = (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        return repository.list(safeOffset, safeLimit)
    }

    override fun get(id: Long): MenuItem? = repository.findById(id)

    override fun create(request: CreateMenuItemRequest): MenuItem =
        repository.create(request.validate())

    override fun update(id: Long, request: UpdateMenuItemRequest): MenuItem? =
        repository.update(id, request.validate())

    override fun delete(id: Long): Boolean = repository.delete(id)

    override fun search(query: String): List<MenuItem> = repository.search(query)

    private companion object {
        const val DEFAULT_OFFSET = 0
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 50
    }
}