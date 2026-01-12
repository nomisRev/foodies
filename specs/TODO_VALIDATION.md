# TODO: Validation to Re-implement

This document tracks all validation logic that was removed from the Foodies Ktor services. This validation needs to be re-implemented in the future.

## Overview

Validation was implemented using the **Kova validation framework** (version 0.0.4):
- `org.komapper:kova-core:0.0.4` - Core validation library
- `org.komapper:kova-ktor:0.0.4` - Ktor integration

The validation used Kotlin context parameters (`-Xcontext-parameters` compiler flag) for a clean DSL-style API.

---

## Menu Service Validation

### File: `menu/src/main/kotlin/io/ktor/foodies/menu/Domain.kt`

#### Imports Removed
```kotlin
import org.komapper.kova.core.Validation
import org.komapper.kova.core.ensureGreaterThan
import org.komapper.kova.core.ensureNotBlank
```

#### Validated Domain Models Removed
```kotlin
data class CreateMenuItem(
    val name: String,
    val description: String,
    val imageUrl: String,
    val price: BigDecimal,
)

data class UpdateMenuItem(
    val name: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val price: BigDecimal? = null,
)
```

#### Validation Functions Removed
```kotlin
context(_: Validation)
fun CreateMenuItemRequest.validate(): CreateMenuItem =
    CreateMenuItem(
        name = name.ensureNotBlank(),
        description = description.ensureNotBlank(),
        imageUrl = imageUrl.ensureNotBlank(),
        price = price.ensureGreaterThan(BigDecimal.ZERO),
    )

context(_: Validation)
fun UpdateMenuItemRequest.validate(): UpdateMenuItem =
    UpdateMenuItem(
        name = name?.ensureNotBlank(),
        description = description?.ensureNotBlank(),
        imageUrl = imageUrl?.ensureNotBlank(),
        price = price?.ensureGreaterThan(BigDecimal.ZERO),
    )
```

#### Validation Rules
- **CreateMenuItemRequest**:
  - `name` must not be blank
  - `description` must not be blank
  - `imageUrl` must not be blank
  - `price` must be greater than zero

- **UpdateMenuItemRequest** (partial updates):
  - `name` (if provided) must not be blank
  - `description` (if provided) must not be blank
  - `imageUrl` (if provided) must not be blank
  - `price` (if provided) must be greater than zero

---

### File: `menu/src/main/kotlin/io/ktor/foodies/menu/Service.kt`

#### Import Removed
```kotlin
import org.komapper.kova.core.validate
```

#### Validation Calls Removed

**In `MenuServiceImpl.create()`:**
```kotlin
override fun create(request: CreateMenuItemRequest): MenuItem =
    repository.create(validate { request.validate() })
```

Changed to:
```kotlin
override fun create(request: CreateMenuItemRequest): MenuItem =
    repository.create(request)
```

**In `MenuServiceImpl.update()`:**
```kotlin
override fun update(id: Long, request: UpdateMenuItemRequest): MenuItem? =
    repository.update(id, validate { request.validate() })
```

Changed to:
```kotlin
override fun update(id: Long, request: UpdateMenuItemRequest): MenuItem? =
    repository.update(id, request)
```

---

### File: `menu/src/main/kotlin/io/ktor/foodies/menu/Repository.kt`

#### Method Signature Changes

**`create()` method:**
```kotlin
// Before
fun create(request: CreateMenuItem): MenuItem

// After
fun create(request: CreateMenuItemRequest): MenuItem
```

**`update()` method:**
```kotlin
// Before
fun update(id: Long, request: UpdateMenuItem): MenuItem?

// After
fun update(id: Long, request: UpdateMenuItemRequest): MenuItem?
```

---

### File: `menu/build.gradle.kts`

#### Dependencies Removed
```kotlin
implementation(libs.kova.core)
implementation(libs.kova.ktor)
```

---

## Basket Service Validation

### File: `basket/src/main/kotlin/io/ktor/foodies/basket/Domain.kt`

#### Imports Removed
```kotlin
import org.komapper.extension.validator.*
import org.komapper.extension.validator.ktor.server.Validated
```

#### Validated Interface Implementation Removed

**From `AddItemRequest`:**
```kotlin
@Serializable
data class AddItemRequest(
    val menuItemId: Long,
    val quantity: Int
) : Validated {
    override fun Validation.validate() = schema {
        ::menuItemId.invoke { notEmpty() }
    }
}
```

Changed to:
```kotlin
@Serializable
data class AddItemRequest(
    val menuItemId: Long,
    val quantity: Int
)
```

#### Validated Domain Models Removed
```kotlin
data class ValidatedAddItem(
    val menuItemId: Long,
    val quantity: Int
)

data class ValidatedUpdateQuantity(
    val quantity: Int
)
```

#### Validation Functions Removed
```kotlin
context(_: Validation)
fun AddItemRequest.validate(): ValidatedAddItem =
    ValidatedAddItem(
        menuItemId = menuItemId.ensureGreaterThan(0L),
        quantity = quantity.ensureAtLeast(1)
    )

context(_: Validation)
fun UpdateItemQuantityRequest.validate(): ValidatedUpdateQuantity =
    ValidatedUpdateQuantity(
        quantity = quantity.ensureAtLeast(1)
    )
```

#### Validation Rules
- **AddItemRequest**:
  - `menuItemId` must be greater than 0
  - `quantity` must be at least 1

- **UpdateItemQuantityRequest**:
  - `quantity` must be at least 1

---

### File: `basket/src/main/kotlin/io/ktor/foodies/basket/Routes.kt`

#### Import Removed
```kotlin
import org.komapper.kova.core.validate
```

#### Validation Calls Removed

**In POST `/basket/items` route:**
```kotlin
post {
    val buyerId = call.principal<JWTPrincipal>()!!.buyerId()
    val request = call.receive<AddItemRequest>()
    val validatedRequest = validate { request.validate() }
    val basket = basketService.addItem(buyerId, validatedRequest)
    if (basket == null) call.respond(HttpStatusCode.NotFound) else call.respond(basket)
}
```

Changed to:
```kotlin
post {
    val buyerId = call.principal<JWTPrincipal>()!!.buyerId()
    val request = call.receive<AddItemRequest>()
    val basket = basketService.addItem(buyerId, request)
    if (basket == null) call.respond(HttpStatusCode.NotFound) else call.respond(basket)
}
```

**In PUT `/basket/items/{itemId}` route:**
```kotlin
put("/{itemId}") {
    val buyerId = call.principal<JWTPrincipal>()!!.buyerId()
    val itemId: String by call.parameters
    val request = call.receive<UpdateItemQuantityRequest>()
    val validatedRequest = validate { request.validate() }
    val basket = basketService.updateItemQuantity(buyerId, itemId, validatedRequest)
    if (basket == null) call.respond(HttpStatusCode.NotFound) else call.respond(basket)
}
```

Changed to:
```kotlin
put("/{itemId}") {
    val buyerId = call.principal<JWTPrincipal>()!!.buyerId()
    val itemId: String by call.parameters
    val request = call.receive<UpdateItemQuantityRequest>()
    val basket = basketService.updateItemQuantity(buyerId, itemId, request)
    if (basket == null) call.respond(HttpStatusCode.NotFound) else call.respond(basket)
}
```

---

### File: `basket/src/main/kotlin/io/ktor/foodies/basket/Service.kt`

#### Method Signature Changes

**`addItem()` method:**
```kotlin
// Before
suspend fun addItem(buyerId: String, request: ValidatedAddItem): CustomerBasket?

// After
suspend fun addItem(buyerId: String, request: AddItemRequest): CustomerBasket?
```

**`updateItemQuantity()` method:**
```kotlin
// Before
suspend fun updateItemQuantity(buyerId: String, itemId: String, request: ValidatedUpdateQuantity): CustomerBasket?

// After
suspend fun updateItemQuantity(buyerId: String, itemId: String, request: UpdateItemQuantityRequest): CustomerBasket?
```

---

### File: `basket/build.gradle.kts`

#### Dependencies Removed
```kotlin
implementation("org.komapper:komapper-kova-core:0.0.4")
implementation("org.komapper:komapper-kova-ktor:0.0.4")
```

---

## Error Handling Removed

### Menu Service

**File**: `menu/src/main/kotlin/io/ktor/foodies/menu/App.kt`

StatusPages exception handler for validation errors (if present):
```kotlin
install(StatusPages) {
    exception<org.komapper.kova.core.ValidationException> { call, cause ->
        call.respondText(
            cause.messages.joinToString("; ") { it.text },
            status = HttpStatusCode.BadRequest
        )
    }
}
```

### Basket Service

**File**: `basket/src/main/kotlin/io/ktor/foodies/basket/App.kt`

StatusPages exception handler for validation errors (if present):
```kotlin
install(StatusPages) {
    exception<org.komapper.kova.core.ValidationException> { call, cause ->
        call.respondText(
            cause.messages.joinToString("; ") { it.text },
            status = HttpStatusCode.BadRequest
        )
    }
}
```

---

## Test Files to Update

### Menu Service Tests

**File**: `menu/src/test/kotlin/io/ktor/foodies/menu/MenuValidationSpec.kt`
- Tests for `CreateMenuItemRequest.validate()` - blank field validation
- Tests for `CreateMenuItemRequest.validate()` - non-positive price validation
- Tests for `UpdateMenuItemRequest.validate()` - blank field validation
- Tests for `UpdateMenuItemRequest.validate()` - non-positive price validation

**File**: `menu/src/test/kotlin/io/ktor/foodies/menu/MenuServiceSpec.kt`
- Update to remove validation expectations

**File**: `menu/src/test/kotlin/io/ktor/foodies/menu/MenuContractSpec.kt`
- Update API contract tests to remove validation error expectations

### Basket Service Tests

**File**: `basket/src/test/kotlin/io/ktor/foodies/basket/BasketValidationSpec.kt`
- Tests for `AddItemRequest.validate()` - menuItemId must be positive
- Tests for `AddItemRequest.validate()` - quantity must be at least 1
- Tests for `UpdateItemQuantityRequest.validate()` - quantity must be at least 1
- Tests for collecting multiple validation errors

**File**: `basket/src/test/kotlin/io/ktor/foodies/basket/BasketRoutesSpec.kt`
- Remove StatusPages configuration for ValidationException
- Update tests that expect 400 BadRequest for invalid inputs

**File**: `basket/src/test/kotlin/io/ktor/foodies/basket/BasketEndToEndSpec.kt`
- Update end-to-end tests if they include validation scenarios

---

## Gradle Version Catalog Changes

### File: `gradle/libs.versions.toml`

#### Version Removed
```toml
kova = "0.0.4"
```

#### Library References Removed
```toml
kova-core = { module = "org.komapper:kova-core", version.ref = "kova" }
kova-ktor = { module = "org.komapper:kova-ktor", version.ref = "kova" }
```

Or similar entries for komapper-kova libraries.

---

## Implementation Notes

### Validation Pattern Used
The validation pattern followed these steps:
1. Define unvalidated request DTOs with `@Serializable`
2. Define validated domain models without serialization
3. Create context-based validation extension functions
4. Call `validate { }` in service layer before passing to repository
5. Handle `ValidationException` in StatusPages for HTTP 400 responses

### Error Response Format
Validation errors were accumulated and returned as:
```
HTTP 400 Bad Request
Body: "field1 error message; field2 error message"
```

### Transaction Safety
Validation occurred before database transactions, ensuring invalid data never reached the repository layer.

---

## Recommended Replacement

When re-implementing validation, consider:
1. **Arrow-kt Validation** - Functional validation with typed errors
2. **Konform** - Kotlin validation DSL
3. **Valiktor** - Type-safe DSL for validating objects
4. **Custom DSL** - Build a lightweight validation DSL specific to project needs
5. **Kova** - Re-add the same framework if it meets requirements

Ensure the replacement maintains:
- Validation in service layer (not routes or repositories)
- Accumulation of multiple validation errors
- Clean separation between unvalidated DTOs and validated domain models
- HTTP 400 responses with detailed error messages
