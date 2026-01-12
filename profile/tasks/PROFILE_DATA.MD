# Profile Data Enhancement Specification

## Overview

Extend the profile service data model to include richer user information similar to eShop's `ApplicationUser`, enabling features like delivery addresses and payment information for the Foodies application.

## Current State

### Existing Schema

```sql
CREATE TABLE profiles (
    id BIGSERIAL PRIMARY KEY,
    subject VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL
);
```

### Current Data Model

```kotlin
data class Profile(
    val id: Long,
    val subject: String,
    val email: String,
    val firstName: String,
    val lastName: String,
)
```

## Proposed Changes

### Extended Schema

```sql
-- V2__add_profile_details.sql
ALTER TABLE profiles
    -- Phone
    ADD COLUMN phone_number VARCHAR(20),

    -- Delivery Address
    ADD COLUMN street VARCHAR(255),
    ADD COLUMN city VARCHAR(100),
    ADD COLUMN state VARCHAR(100),
    ADD COLUMN country VARCHAR(100),
    ADD COLUMN zip_code VARCHAR(20),

    -- Payment Information (encrypted at rest)
    ADD COLUMN card_holder_name VARCHAR(255),
    ADD COLUMN card_number_last_four CHAR(4),
    ADD COLUMN card_type VARCHAR(20),
    ADD COLUMN card_expiration VARCHAR(7),

    -- Metadata
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
```

### Extended Data Model

```kotlin
@Serializable
data class Profile(
    val id: Long,
    val subject: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String?,
    val address: Address?,
    val paymentInfo: PaymentInfo?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class Address(
    val street: String,
    val city: String,
    val state: String,
    val country: String,
    val zipCode: String,
)

@Serializable
data class PaymentInfo(
    val cardHolderName: String,
    val cardNumberLastFour: String,
    val cardType: CardType,
    val cardExpiration: String,
)

enum class CardType {
    VISA, MASTERCARD, AMEX, DISCOVER, OTHER
}
```

### Extended Exposed Table

```kotlin
object ProfileTable : LongIdTable("profiles") {
    val subject = varchar("subject", 255).uniqueIndex()
    val email = varchar("email", 255)
    val firstName = varchar("first_name", 255)
    val lastName = varchar("last_name", 255)

    // Phone
    val phoneNumber = varchar("phone_number", 20).nullable()

    // Address
    val street = varchar("street", 255).nullable()
    val city = varchar("city", 100).nullable()
    val state = varchar("state", 100).nullable()
    val country = varchar("country", 100).nullable()
    val zipCode = varchar("zip_code", 20).nullable()

    // Payment (store only last 4 digits)
    val cardHolderName = varchar("card_holder_name", 255).nullable()
    val cardNumberLastFour = char("card_number_last_four", 4).nullable()
    val cardType = varchar("card_type", 20).nullable()
    val cardExpiration = varchar("card_expiration", 7).nullable()

    // Metadata
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)
}
```

## Event Model Updates

### Extended UserEvent

The `keycloak-events` module should be updated to support additional profile fields:

```kotlin
sealed interface UserEvent {
    val subject: String

    data class Registration(
        override val subject: String,
        val email: String,
        val firstName: String,
        val lastName: String,
        val phoneNumber: String? = null,
    ) : UserEvent

    data class UpdateProfile(
        override val subject: String,
        val email: String,
        val firstName: String,
        val lastName: String,
        val phoneNumber: String? = null,
    ) : UserEvent

    data class Delete(override val subject: String) : UserEvent
}
```

**Note**: Address and payment information should NOT come from Keycloak events. These are user-entered data managed directly by the profile service via REST API.

## New REST API Endpoints

The profile service should expose REST endpoints for managing profile data that doesn't come from Keycloak:

### Endpoints

```
GET    /profiles/me              # Get current user's profile (requires JWT)
PUT    /profiles/me/address      # Update delivery address
DELETE /profiles/me/address      # Remove delivery address
PUT    /profiles/me/payment      # Update payment info
DELETE /profiles/me/payment      # Remove payment info
```

### Request/Response Models

```kotlin
@Serializable
data class AddressRequest(
    val street: String,
    val city: String,
    val state: String,
    val country: String,
    val zipCode: String,
)

@Serializable
data class PaymentRequest(
    val cardHolderName: String,
    val cardNumber: String,      // Full number, only last 4 stored
    val cardType: CardType,
    val cardExpiration: String,  // MM/YYYY format
)

@Serializable
data class ProfileResponse(
    val subject: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String?,
    val address: Address?,
    val hasPaymentInfo: Boolean, // Don't expose actual payment details
)
```

## Security Considerations

1. **Payment Data**: Never store full card numbers. Only store last 4 digits for display purposes. Full payment processing should use a payment provider (Stripe, etc.)

2. **JWT Validation**: All profile management endpoints must validate the JWT and only allow users to modify their own profile

3. **Audit Trail**: Consider adding audit logging for profile changes

4. **Data Encryption**: Consider encrypting sensitive fields at rest (card info, address)

## Repository Interface Updates

```kotlin
interface ProfileRepository {
    // Existing
    fun findBySubject(subject: String): Profile?
    fun insertOrIgnore(subject: String, email: String, firstName: String, lastName: String): Long?
    fun upsert(subject: String, email: String, firstName: String, lastName: String)
    fun deleteBySubject(subject: String): Boolean

    // New
    fun updateAddress(subject: String, address: Address): Boolean
    fun clearAddress(subject: String): Boolean
    fun updatePaymentInfo(subject: String, payment: PaymentInfo): Boolean
    fun clearPaymentInfo(subject: String): Boolean
}
```

## Implementation Steps

1. **Phase 1: Schema Migration**
   - Create Flyway migration `V2__add_profile_details.sql`
   - Update `ProfileTable` with new columns
   - Update `Profile` data class

2. **Phase 2: Repository Updates**
   - Add new repository methods for address/payment management
   - Write tests for new repository methods

3. **Phase 3: REST API**
   - Add authentication (JWT validation)
   - Implement profile management endpoints
   - Add input validation
   - Write API contract tests

4. **Phase 4: Event Updates (Optional)**
   - Extend `UserEvent` to include phone number
   - Update Keycloak event publisher to extract phone
   - Update consumer to handle new fields

## Testing Requirements

- Unit tests for new repository methods
- Integration tests with PostgreSQL testcontainer
- API contract tests for new endpoints
- Security tests for authorization

## Dependencies

- No new dependencies required for basic implementation
- Consider adding `ktor-server-auth-jwt` if not already present for JWT validation

## Comparison with eShop

| Feature | eShop | Foodies (Proposed) |
|---------|-------|-------------------|
| Address Fields | street, city, state, country, zipCode | Same |
| Payment Storage | Full card details | Last 4 digits only |
| Profile Source | Direct from Identity DB | Keycloak + REST API |
| Claims in JWT | All profile data | Basic info only |

## Open Questions

1. Should we store multiple addresses (home, work, etc.)?
2. Should payment info integrate with a payment provider instead of local storage?
3. Do we need profile versioning/history?
