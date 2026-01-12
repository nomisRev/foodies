# Foodies WebApp Enhancement Specification

## Overview

This specification outlines the features and enhancements required to bring the Foodies WebApp on par with the eShop Blazor WebApp. The goal is to transform the current read-only menu browsing interface into a full-featured food ordering platform.

## Current State vs Target State

| Feature | Current State | Target State |
|---------|--------------|--------------|
| Menu Browsing | ✅ Infinite scroll | ✅ Enhanced with filtering/search |
| Authentication | ✅ OAuth2/OIDC | ✅ Maintain + enhance session |
| Shopping Cart | ❌ Not implemented | ✅ Full cart functionality |
| Checkout | ❌ Not implemented | ✅ Complete checkout flow |
| Order Management | ❌ Not implemented | ✅ Order history & tracking |
| User Profile | ⚠️ Auth only | ✅ Profile page & management |
| Menu Details | ❌ Not implemented | ✅ Individual item pages |
| Search & Filter | ❌ Not implemented | ✅ Category & search support |
| Real-time Updates | ❌ Not implemented | ✅ Order status notifications |

---

## Phase 1: Menu Enhancements

### 1.1 Menu Item Detail Page

**Route**: `GET /menu/{id}`

**Description**: Individual menu item page with full details and add-to-cart functionality.

**UI Components**:
- Hero image (large, responsive)
- Item name and description
- Price display (formatted: `$XX.XX`)
- Category/type badge
- Nutritional info (optional)
- Add to Cart button (requires authentication)
- Quantity selector
- Back to menu link

**Technical Implementation**:
```kotlin
// MenuRoutes.kt
get("/menu/{id}") {
    val id = call.parameters["id"]?.toLongOrNull()
        ?: return@get call.respond(HttpStatusCode.BadRequest)

    val item = menuService.getMenuItem(id)
        ?: return@get call.respond(HttpStatusCode.NotFound)

    val user = call.sessions.get<UserInfo>()
    call.respondHtml { menuItemDetailPage(item, user) }
}
```

**MenuService Extension**:
```kotlin
interface MenuService {
    suspend fun menuItems(offset: Int, limit: Int): List<MenuItem>
    suspend fun getMenuItem(id: Long): MenuItem?          // NEW
    suspend fun getCategories(): List<Category>           // NEW
    suspend fun searchMenuItems(query: String): List<MenuItem>  // NEW
}
```

### 1.2 Menu Categories & Filtering

**Route**: `GET /menu?category={categoryId}&page={page}`

**Description**: Filter menu items by category (appetizers, mains, desserts, drinks).

**UI Components**:
- Category sidebar/tabs (responsive)
- "All" category option
- Active category highlighting
- Category badges on menu cards
- Persistent filter state via query params

**Data Model**:
```kotlin
@Serializable
data class Category(
    val id: Long,
    val name: String,
    val description: String,
    val iconUrl: String?
)

@Serializable
data class MenuItem(
    val id: Long,
    val name: String,
    val description: String,
    val imageUrl: String,
    val price: SerializableBigDecimal,
    val categoryId: Long,          // NEW
    val categoryName: String       // NEW (denormalized for display)
)
```

### 1.3 Menu Search

**Route**: `GET /menu?search={query}`

**Description**: Text-based search across menu item names and descriptions.

**UI Components**:
- Search input field in header
- HTMX live search (`hx-trigger="keyup changed delay:300ms"`)
- Search results with highlighting
- "No results" state
- Clear search button

**HTMX Implementation**:
```html
<input type="search"
       name="search"
       placeholder="Search menu..."
       hx-get="/menu"
       hx-trigger="keyup changed delay:300ms"
       hx-target="#menu-grid"
       hx-swap="innerHTML"
       hx-indicator="#search-spinner" />
```

---

## Phase 2: Shopping Cart

### 2.1 Cart State Management

**Description**: Server-side cart state management with session binding.

**Service Interface**:
```kotlin
interface BasketService {
    suspend fun getBasket(userId: String): Basket
    suspend fun addItem(userId: String, item: BasketItem): Basket
    suspend fun updateQuantity(userId: String, itemId: Long, quantity: Int): Basket
    suspend fun removeItem(userId: String, itemId: Long): Basket
    suspend fun clearBasket(userId: String)
}

@Serializable
data class Basket(
    val userId: String,
    val items: List<BasketItem>,
    val totalItems: Int,
    val totalPrice: SerializableBigDecimal
)

@Serializable
data class BasketItem(
    val menuItemId: Long,
    val name: String,
    val imageUrl: String,
    val unitPrice: SerializableBigDecimal,
    val quantity: Int
)
```

**Integration Options**:
1. **Redis-backed** (recommended for production)
2. **PostgreSQL-backed** (simpler, sufficient for MVP)
3. **Separate Basket microservice** (as per existing specification)

### 2.2 Add to Cart Functionality

**Route**: `POST /cart/items`

**Description**: Add menu item to cart from detail page or menu grid.

**Request Body**:
```kotlin
@Serializable
data class AddToCartRequest(
    val menuItemId: Long,
    val quantity: Int = 1
)
```

**UI Components**:
- Add to Cart button (primary action)
- Quantity input (optional, default: 1)
- Success feedback (HTMX OOB swap)
- Cart icon badge update
- Requires authentication redirect

**HTMX Implementation**:
```html
<form hx-post="/cart/items"
      hx-swap="none"
      hx-on::after-request="if(event.detail.successful) htmx.trigger('#cart-badge', 'cart-updated')">
    <input type="hidden" name="menuItemId" value="${item.id}" />
    <input type="number" name="quantity" value="1" min="1" max="10" />
    <button type="submit">Add to Cart</button>
</form>
```

### 2.3 Cart Page

**Route**: `GET /cart` (requires authentication)

**Description**: Full cart management interface.

**UI Components**:
- Cart item list with:
  - Item image (thumbnail)
  - Item name and unit price
  - Quantity editor (+/- buttons or input)
  - Item subtotal
  - Remove button
- Cart summary:
  - Subtotal
  - Estimated tax (optional)
  - Total price
  - Item count
- Action buttons:
  - Continue Shopping (link to `/`)
  - Proceed to Checkout (link to `/checkout`)
- Empty cart state with CTA

**HTMX Interactions**:
- Quantity update: `PATCH /cart/items/{id}` with optimistic UI
- Remove item: `DELETE /cart/items/{id}` with OOB swap
- Real-time total recalculation

### 2.4 Cart Menu Component

**Description**: Header cart icon with item count badge.

**UI Components**:
- Shopping bag icon
- Item count badge (hidden when 0)
- Clickable link to `/cart`

**Real-time Updates**:
```html
<a href="/cart" id="cart-badge"
   hx-get="/cart/badge"
   hx-trigger="cart-updated from:body, load">
    <svg><!-- cart icon --></svg>
    <span class="badge">${itemCount}</span>
</a>
```

---

## Phase 3: Checkout Flow

### 3.1 Checkout Page

**Route**: `GET /checkout` (requires authentication)

**Description**: Shipping and payment information collection.

**UI Components**:
- Order summary (collapsed cart view)
- Shipping address form:
  - Street address (required)
  - City (required)
  - State/Province (required)
  - Postal code (required)
  - Country (required, dropdown)
  - Phone number (optional)
- Payment section (simplified for MVP):
  - Cash on delivery option
  - Credit card integration (future)
- Special instructions textarea
- Place Order button
- Form validation (client + server)

**Data Model**:
```kotlin
@Serializable
data class CheckoutRequest(
    val street: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String,
    val phoneNumber: String?,
    val specialInstructions: String?,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH_ON_DELIVERY
)

enum class PaymentMethod {
    CASH_ON_DELIVERY,
    CREDIT_CARD  // Future
}
```

### 3.2 Order Submission

**Route**: `POST /checkout`

**Description**: Create order from cart and redirect to confirmation.

**Flow**:
1. Validate checkout form
2. Validate cart is not empty
3. Create order via Ordering service
4. Clear basket on success
5. Redirect to order confirmation page

**Response**:
- Success: Redirect to `/orders/{orderId}`
- Validation error: Re-render form with errors
- Server error: Display error message

### 3.3 Order Confirmation Page

**Route**: `GET /orders/{orderId}`

**Description**: Order confirmation and details display.

**UI Components**:
- Success message with order number
- Order summary:
  - Items ordered
  - Delivery address
  - Payment method
  - Order total
- Estimated delivery time (if available)
- Links:
  - View All Orders
  - Continue Shopping
  - Track Order (if supported)

---

## Phase 4: Order Management

### 4.1 Order History Page

**Route**: `GET /orders` (requires authentication)

**Description**: List of user's past and current orders.

**UI Components**:
- Orders table/list:
  - Order number
  - Order date
  - Total amount
  - Status badge
  - View details link
- Empty state with CTA
- Pagination (if many orders)

**Data Model**:
```kotlin
@Serializable
data class OrderSummary(
    val id: Long,
    val orderNumber: String,
    val date: Instant,
    val status: OrderStatus,
    val totalAmount: SerializableBigDecimal,
    val itemCount: Int
)

enum class OrderStatus {
    SUBMITTED,
    AWAITING_CONFIRMATION,
    CONFIRMED,
    PREPARING,
    READY_FOR_PICKUP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED
}
```

### 4.2 Order Detail Page

**Route**: `GET /orders/{orderId}` (requires authentication)

**Description**: Detailed view of a specific order.

**UI Components**:
- Order header:
  - Order number
  - Order date/time
  - Status with timeline
- Items list:
  - Item image, name, quantity
  - Unit price and subtotal
- Delivery information:
  - Address
  - Special instructions
- Payment summary:
  - Subtotal
  - Tax
  - Total
- Actions (if applicable):
  - Cancel order (if status allows)
  - Reorder

### 4.3 Real-time Order Status Updates

**Description**: Live order status updates via Server-Sent Events (SSE) or WebSocket.

**Implementation Options**:

**Option A: Server-Sent Events (SSE)**
```kotlin
// OrderRoutes.kt
get("/orders/{orderId}/status/stream") {
    val orderId = call.parameters["orderId"]?.toLongOrNull() ?: return@get

    call.respondSse {
        orderService.statusUpdates(orderId).collect { status ->
            send(ServerSentEvent(data = Json.encodeToString(status)))
        }
    }
}
```

**Option B: HTMX Polling**
```html
<div id="order-status"
     hx-get="/orders/${orderId}/status"
     hx-trigger="every 10s"
     hx-swap="outerHTML">
    <!-- Status badge -->
</div>
```

**Option C: RabbitMQ Integration Events**
- Subscribe to `OrderStatusChanged` events
- Push updates via WebSocket connection
- Most scalable option for microservices

---

## Phase 5: User Profile

### 5.1 Profile Page

**Route**: `GET /profile` (requires authentication)

**Description**: User account information and settings.

**UI Components**:
- User info card:
  - Name
  - Email
  - Member since
- Saved addresses section
- Quick links:
  - Order History
  - Logout

### 5.2 Address Management

**Route**: `GET /profile/addresses`

**Description**: Manage saved delivery addresses.

**UI Components**:
- Address list
- Default address indicator
- Add new address form
- Edit/Delete actions
- Set as default action

**Data Model**:
```kotlin
@Serializable
data class SavedAddress(
    val id: Long,
    val label: String,  // "Home", "Work", etc.
    val street: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String,
    val isDefault: Boolean
)
```

---

## Phase 6: Header & Navigation

### 6.1 Enhanced Header

**UI Components**:
- Logo (link to home)
- Search bar (expandable on mobile)
- Navigation links:
  - Menu
  - Categories dropdown (optional)
- User menu:
  - When logged in: User name, dropdown with Profile/Orders/Logout
  - When logged out: Login button
- Cart icon with badge

**Responsive Behavior**:
- Desktop: Full header with all elements
- Mobile: Hamburger menu with drawer

### 6.2 User Menu Component

**Description**: Authenticated user menu with dropdown.

```kotlin
fun FlowContent.userMenu(user: UserInfo?) {
    if (user != null) {
        div(classes = "user-menu") {
            button(classes = "user-menu-toggle") {
                +user.name.orEmpty()
                // Dropdown arrow icon
            }
            div(classes = "user-menu-dropdown") {
                a(href = "/profile") { +"Profile" }
                a(href = "/orders") { +"Orders" }
                form(action = "/logout", method = FormMethod.post) {
                    button(type = ButtonType.submit) { +"Logout" }
                }
            }
        }
    } else {
        a(href = "/login", classes = "login-button") { +"Login" }
    }
}
```

---

## Technical Requirements

### API Integrations

**Menu Service** (existing, enhanced):
```
GET  /menu                    - List items (pagination, filters)
GET  /menu/{id}               - Get single item
GET  /menu/categories         - List categories
GET  /menu/search?q={query}   - Search items
```

**Basket Service** (new):
```
GET    /basket                - Get user's basket
POST   /basket/items          - Add item
PATCH  /basket/items/{id}     - Update quantity
DELETE /basket/items/{id}     - Remove item
DELETE /basket                - Clear basket
```

**Ordering Service** (new):
```
GET  /orders                  - List user's orders
GET  /orders/{id}             - Get order details
POST /orders                  - Create order
POST /orders/{id}/cancel      - Cancel order
```

### Database Schema Extensions

**Menu Database** (menu service):
```sql
-- Categories table
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    icon_url TEXT,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Add category_id to menu_items
ALTER TABLE menu_items
ADD COLUMN category_id BIGINT REFERENCES categories(id);

-- Full-text search index
CREATE INDEX menu_items_search_idx ON menu_items
USING gin(to_tsvector('english', name || ' ' || description));
```

**Basket Database** (or Redis):
```sql
CREATE TABLE baskets (
    user_id VARCHAR(255) PRIMARY KEY,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE basket_items (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL REFERENCES baskets(user_id),
    menu_item_id BIGINT NOT NULL,
    name TEXT NOT NULL,
    image_url TEXT,
    unit_price NUMERIC(10, 2) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, menu_item_id)
);
```

**Orders Database** (ordering service):
```sql
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    user_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'SUBMITTED',
    subtotal NUMERIC(10, 2) NOT NULL,
    tax NUMERIC(10, 2) NOT NULL DEFAULT 0,
    total NUMERIC(10, 2) NOT NULL,
    -- Delivery info
    street TEXT NOT NULL,
    city TEXT NOT NULL,
    state TEXT NOT NULL,
    postal_code TEXT NOT NULL,
    country TEXT NOT NULL,
    phone_number TEXT,
    special_instructions TEXT,
    -- Timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    menu_item_id BIGINT NOT NULL,
    name TEXT NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL,
    quantity INT NOT NULL,
    subtotal NUMERIC(10, 2) NOT NULL
);

CREATE TABLE order_status_history (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    status VARCHAR(50) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by VARCHAR(255)
);
```

### Session Management

**Redis Session Storage** (for load balancing):
```kotlin
install(Sessions) {
    cookie<UserSession>("USER_SESSION") {
        cookie.httpOnly = true
        cookie.secure = true  // Production
        cookie.sameSite = SameSiteCookie.LAX
        storage = RedisSessionStorage(redisClient, "sessions:")
    }
}
```

### Error Handling

**Standard Error Response**:
```kotlin
@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null
)
```

**Error Pages**:
- 404 Not Found: Menu item not found, order not found
- 401 Unauthorized: Redirect to login with return URL
- 403 Forbidden: Access denied page
- 500 Server Error: Generic error page with retry option

---

## UI/UX Design Guidelines

### Color Palette
- Primary: Orange (#F97316) - CTA buttons, highlights
- Secondary: Slate (#1E293B) - Background, text
- Success: Green (#22C55E) - Success states, confirmations
- Warning: Yellow (#EAB308) - Warnings, pending states
- Error: Red (#EF4444) - Errors, validation
- Neutral: Gray (#64748B) - Secondary text, borders

### Typography
- Headings: Plus Jakarta Sans (or Inter)
- Body: Open Sans (or Inter)
- Monospace: JetBrains Mono (for order numbers)

### Component Library
- Buttons: Primary, Secondary, Ghost, Danger
- Forms: Input, Select, Textarea, Checkbox
- Cards: Menu item card, Order card, Address card
- Badges: Status badges, Category badges, Count badges
- Modals: Confirmation dialogs, Address forms
- Notifications: Toast messages, Inline alerts

### Responsive Breakpoints
- Mobile: < 640px
- Tablet: 640px - 1024px
- Desktop: > 1024px

---

## Testing Requirements

### Unit Tests
- MenuService: getMenuItem, searchMenuItems, getCategories
- BasketService: addItem, updateQuantity, removeItem, clearBasket
- OrderService: createOrder, getOrders, cancelOrder

### Integration Tests
- Full checkout flow: Add items → Checkout → Confirm
- Authentication flow: Login → Browse → Logout
- Cart persistence: Add items → Refresh → Items preserved

### E2E Tests
- User journey: Browse → Add to cart → Checkout → View order
- Error scenarios: Empty cart checkout, invalid address
- Edge cases: Concurrent cart updates, network failures

---

## Implementation Priority

### MVP (Phase 1-2)
1. Menu item detail page
2. Basic cart functionality (add, view, remove)
3. Cart badge in header

### Core Features (Phase 3)
4. Checkout page and order creation
5. Order confirmation page
6. Basic order history

### Enhanced Experience (Phase 4-5)
7. Menu categories and filtering
8. Menu search
9. Order status tracking
10. User profile page

### Advanced Features (Phase 6+)
11. Real-time order updates (SSE/WebSocket)
12. Saved addresses
13. Reorder functionality
14. AI-powered recommendations (future)

---

## Configuration

### Environment Variables

```yaml
# WebApp Configuration
config:
  host: "$HOST:0.0.0.0"
  port: "$PORT:8080"

  # Authentication
  security:
    issuer: "$AUTH_ISSUER:http://localhost:8000/realms/foodies-keycloak"
    clientId: "$AUTH_CLIENT_ID:foodies"
    clientSecret: "$AUTH_CLIENT_SECRET:foodies_client_secret"

  # Service URLs
  services:
    menu: "$MENU_BASE_URL:http://localhost:8082"
    basket: "$BASKET_BASE_URL:http://localhost:8083"
    ordering: "$ORDERING_BASE_URL:http://localhost:8084"

  # Session (optional Redis)
  session:
    redisUrl: "$REDIS_URL:"  # Empty = in-memory
    cookieMaxAge: "$SESSION_MAX_AGE:3600"  # 1 hour
```

---

## Deployment Considerations

### New Services Required
1. **Basket Service** (port 8083) - Cart management
2. **Ordering Service** (port 8084) - Order processing

### Infrastructure
- Redis: Session storage, basket caching
- PostgreSQL: Orders database
- RabbitMQ: Order events (existing)

### Kubernetes Updates
- Add basket service deployment
- Add ordering service deployment
- Update webapp configmap with new service URLs
- Add Redis deployment (or use managed service)

---

## Appendix: Feature Comparison with eShop

| eShop Feature | Foodies Equivalent | Priority |
|---------------|-------------------|----------|
| Product catalog with pagination | Menu browsing (existing) | ✅ Done |
| Brand/Type filtering | Category filtering | High |
| Semantic search (AI) | Text search | Medium |
| Shopping cart (gRPC) | Shopping cart (HTTP) | High |
| Checkout with address | Checkout flow | High |
| Order history | Order management | High |
| Order status tracking | Real-time updates | Medium |
| User profile | Profile page | Medium |
| AI Chatbot | Future consideration | Low |
| Real-time status (EventBus) | SSE or polling | Medium |

---

## References

- eShop Reference: https://github.com/dotnet/eShop
- Ktor Documentation: https://ktor.io/
- HTMX Documentation: https://htmx.org/
- Existing Basket Specification: `/basket/SPECIFICATION.md`
