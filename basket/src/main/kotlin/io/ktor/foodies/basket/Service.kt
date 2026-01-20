package io.ktor.foodies.basket

/**
 * Service interface for basket operations.
 */
interface BasketService {
    /**
     * Gets the basket for the specified buyer.
     * Returns an empty basket if none exists.
     */
    suspend fun getBasket(buyerId: String): CustomerBasket

    /**
     * Adds an item to the buyer's basket.
     * If the menu item already exists, increments the quantity.
     *
     * @return null if the menu item doesn't exist
     */
    suspend fun addItem(buyerId: String, request: ValidatedAddItem, token: String): CustomerBasket?

    /**
     * Updates the quantity of a specific basket item.
     *
     * @return null if the item is not in the basket
     */
    suspend fun updateItemQuantity(buyerId: String, itemId: String, request: ValidatedUpdateQuantity): CustomerBasket?

    /**
     * Removes an item from the basket.
     *
     * @return null if the item is not in the basket
     */
    suspend fun removeItem(buyerId: String, itemId: String): CustomerBasket?

    /**
     * Clears all items from the basket.
     */
    suspend fun clearBasket(buyerId: String)
}

class BasketServiceImpl(
    private val repository: BasketRepository,
    private val menuClient: MenuClient
) : BasketService {

    override suspend fun getBasket(buyerId: String): CustomerBasket {
        return repository.getBasket(buyerId) ?: CustomerBasket(buyerId)
    }

    override suspend fun addItem(buyerId: String, request: ValidatedAddItem, token: String): CustomerBasket? {
        // Fetch menu item to validate existence and get current details
        val menuItem = menuClient.getMenuItem(request.menuItemId, token) ?: return null

        val basket = repository.getBasket(buyerId) ?: CustomerBasket(buyerId)

        // Check if item already exists in basket
        val existingItemIndex = basket.items.indexOfFirst { it.menuItemId == request.menuItemId }

        val updatedBasket = if (existingItemIndex >= 0) {
            // Increment quantity of existing item
            val existingItem = basket.items[existingItemIndex]
            val updatedItem = existingItem.copy(quantity = existingItem.quantity + request.quantity)
            val updatedItems = basket.items.toMutableList().apply {
                set(existingItemIndex, updatedItem)
            }
            basket.copy(items = updatedItems)
        } else {
            // Add new item with denormalized menu data
            val newItem = createBasketItem(
                menuItemId = menuItem.id,
                menuItemName = menuItem.name,
                menuItemDescription = menuItem.description,
                menuItemImageUrl = menuItem.imageUrl,
                unitPrice = menuItem.price,
                quantity = request.quantity
            )
            basket.copy(items = basket.items + newItem)
        }

        return repository.updateBasket(updatedBasket)
    }

    override suspend fun updateItemQuantity(buyerId: String, itemId: String, request: ValidatedUpdateQuantity): CustomerBasket? {
        val basket = repository.getBasket(buyerId) ?: return null

        val itemIndex = basket.items.indexOfFirst { it.id == itemId }
        if (itemIndex < 0) return null

        val updatedItem = basket.items[itemIndex].copy(quantity = request.quantity)
        val updatedItems = basket.items.toMutableList().apply {
            set(itemIndex, updatedItem)
        }
        val updatedBasket = basket.copy(items = updatedItems)

        return repository.updateBasket(updatedBasket)
    }

    override suspend fun removeItem(buyerId: String, itemId: String): CustomerBasket? {
        val basket = repository.getBasket(buyerId) ?: return null

        val itemExists = basket.items.any { it.id == itemId }
        if (!itemExists) return null

        val updatedItems = basket.items.filter { it.id != itemId }
        val updatedBasket = basket.copy(items = updatedItems)

        return repository.updateBasket(updatedBasket)
    }

    override suspend fun clearBasket(buyerId: String) {
        repository.deleteBasket(buyerId)
    }
}
