# Foodies - Order & Payment Service Issues

This document contains a list of isolated issues identified by comparing the ORDER_SPEC.md and PAYMENT_SPEC.md specifications with the current implementation.

## Order Service Issues

### 1. [DONE] Missing OrderStockConfirmedEvent to Payment Service
**File:** `order/src/main/kotlin/io/ktor/foodies/order/events/handlers/StockConfirmedEventHandler.kt`
**Issue:** When stock is confirmed, the Order service should publish an `OrderStockConfirmedEvent` that triggers payment processing. Currently, it only updates the order status internally.
**Expected:** After `setStockConfirmed()` succeeds, publish `OrderStockConfirmedEvent` with payment method details to trigger the Payment service.
**Priority:** High
**Spec Reference:** ORDER_SPEC.md lines 92-95, 176-198

### 2. [DONE] Missing Payment Method Storage in Order Domain
**File:** `order/src/main/kotlin/io/ktor/foodies/order/domain/Order.kt`
**Issue:** The Order domain model does not store or pass payment method information needed by the Payment service.
**Expected:** Order should store payment method details (card type, last 4 digits, expiration) from `CreateOrderRequest.paymentDetails` and include them in `OrderStockConfirmedEvent`.
**Priority:** High
**Spec Reference:** ORDER_SPEC.md lines 100, 152-169

### 3. [DONE] Incorrect Port Configuration for Payment Service
**File:** `specs/PAYMENT_SPEC.md` line 15
**Issue:** PAYMENT_SPEC.md states port 8084, but this conflicts with ORDER service which also uses 8084 (ORDER_SPEC.md line 15).
**Expected:** Payment service should use a different port (e.g., 8085).
**Priority:** Medium
**Spec Reference:** PAYMENT_SPEC.md line 15, ORDER_SPEC.md line 15

### 4. [DONE] Missing Admin Route Authentication
**File:** `order/src/main/kotlin/io/ktor/foodies/order/AdminRoutes.kt`
**Issue:** Admin routes should verify the user has the 'admin' role, but this check is not visible in the routing setup.
**Expected:** Admin routes must check for admin role from JWT claims before allowing access.
**Priority:** High
**Spec Reference:** ORDER_SPEC.md lines 481-505

### 5. [DONE] Missing POST /orders Response Description Field
**File:** `order/src/main/kotlin/io/ktor/foodies/order/Routes.kt`
**Issue:** The POST /orders endpoint should return the full order including `description` field per the spec.
**Expected:** Ensure the response includes all Order fields including the optional `description`.
**Priority:** Low
**Spec Reference:** ORDER_SPEC.md lines 321-335

### 6. [DONE] Missing CancelOrderRequest Structure
**File:** `order/src/main/kotlin/io/ktor/foodies/order/domain/*`
**Issue:** The PUT /orders/{orderId}/cancel endpoint expects an optional request body per the spec, but implementation uses `CancelOrderRequest` with a `reason` field.
**Expected:** Spec shows no request body, but implementation requires one. Clarify if reason should be optional or in query param.
**Priority:** Low
**Spec Reference:** ORDER_SPEC.md lines 432-455

### 7. [DONE] Order Port Configuration Mismatch
**File:** `order/src/main/kotlin/io/ktor/foodies/order/Config.kt`
**Issue:** Default port in Config.kt is 8080, but spec says 8084.
**Expected:** Default port should match spec (8084).
**Priority:** Low
**Spec Reference:** ORDER_SPEC.md line 15

## Payment Service Issues

### 8. [DONE] Missing OrderStockConfirmedEvent Publication from Order Service
**File:** Integration gap between services
**Issue:** Payment service consumes `OrderStockConfirmedEvent`, but Order service doesn't publish it after stock confirmation.
**Expected:** Order service must publish this event when transitioning to StockConfirmed status.
**Priority:** High
**Spec Reference:** PAYMENT_SPEC.md lines 176-198

### 9. [DONE] Event Field Name Mismatch: paymentId vs transactionId
**File:** `payment/src/main/kotlin/io/ktor/foodies/payment/events/Events.kt`
**Issue:** `OrderPaymentSucceededEvent` has `paymentId` (Long) but Order service expects `paymentId` as String based on `PaymentSucceededEvent.paymentId`.
**Expected:** Align the type - spec shows `paymentId: Long` and `transactionId: String` separately.
**Priority:** Medium
**Spec Reference:** ORDER_SPEC.md lines 682-694, PAYMENT_SPEC.md lines 206-216

### 10. [DONE] Missing Currency Field in Order Events
**File:** `order/src/main/kotlin/io/ktor/foodies/order/domain/OrderEvents.kt`
**Issue:** Payment service expects `currency` field in `OrderStockConfirmedEvent`, but Order service events don't include currency.
**Expected:** Add currency field (e.g., "USD") to Order domain and include in stock confirmed event.
**Priority:** High
**Spec Reference:** PAYMENT_SPEC.md lines 183-191

### 11. [DONE] Payment Method Info Mapping
**File:** Integration gap
**Issue:** Order service stores payment details differently than Payment service expects (`PaymentMethodInfo`).
**Expected:** Create mapping between Order's `PaymentMethod` and Payment's `PaymentMethodInfo`.
**Priority:** High
**Spec Reference:** PAYMENT_SPEC.md lines 144-171, ORDER_SPEC.md lines 152-169

### 12. [DONE] RabbitMQ Routing Key Mismatch
**File:** `payment/src/main/kotlin/io/ktor/foodies/payment/Config.kt`
**Issue:** Payment service consumes from queue bound to routing key, but Order service's routing key for stock-confirmed events needs verification.
**Expected:** Ensure Order service publishes `OrderStockConfirmedEvent` with routing key `order.stock-confirmed` to match Payment service's queue binding.
**Priority:** High
**Spec Reference:** PAYMENT_SPEC.md line 197

### 13. [DONE] Missing GET /payments/{orderId} Admin Endpoint
**File:** `payment/src/main/kotlin/io/ktor/foodies/payment/Routes.kt`
**Issue:** Spec defines GET /payments/{orderId} admin endpoint, but current implementation status is unclear.
**Expected:** Implement admin-only endpoint to retrieve payment details by order ID.
**Priority:** Low
**Spec Reference:** PAYMENT_SPEC.md lines 726-757

## Data Model Issues

### 14. [DONE] CardType vs CardBrand Naming Inconsistency
**File:** `order/src/main/kotlin/io/ktor/foodies/order/domain/CardType.kt` vs `payment/src/main/kotlin/io/ktor/foodies/payment/Domain.kt`
**Issue:** Order service uses `CardType` enum, Payment service uses `CardBrand` enum. Both represent the same concept.
**Expected:** Use consistent naming across services, preferably `CardBrand` as per PAYMENT_SPEC.
**Priority:** Low
**Spec Reference:** ORDER_SPEC.md lines 163-168, PAYMENT_SPEC.md lines 149-171

### 15. [DONE] SerializableBigDecimal vs BigDecimal Usage
**File:** Multiple files
**Issue:** Inconsistent usage of `SerializableBigDecimal` in events/domain vs `BigDecimal` in internal logic.
**Expected:** Ensure proper conversion between types and consistent serialization.
**Priority:** Low
**Impact:** May cause serialization issues in events

## Configuration Issues

### 16. Missing Payment Service URL in Order Config
**File:** `order/src/main/kotlin/io/ktor/foodies/order/Config.kt`
**Issue:** Order service may need to know Payment service URL for direct HTTP calls (if any) or for health check dependencies.
**Expected:** Consider if any direct HTTP communication is needed between services (currently only event-driven).
**Priority:** Low

### 17. [DONE] Payment Service Port Not Configurable via Default
**File:** `specs/PAYMENT_SPEC.md`
**Issue:** Payment spec shows port 8084 which conflicts with Order service.
**Expected:** Update spec to use port 8085 or another available port.
**Priority:** Medium
**Spec Reference:** PAYMENT_SPEC.md line 15

## Event Flow Issues

### 18. [DONE] Missing OrderStockConfirmedEvent Definition in Order Service Events
**File:** `order/src/main/kotlin/io/ktor/foodies/order/domain/OrderEvents.kt`
**Issue:** This event should be published by Order service but is not defined in Order service events file. It's only consumed, never published.
**Expected:** Define and publish `OrderStockConfirmedEvent` with all required fields for Payment service.
**Priority:** High
**Spec Reference:** PAYMENT_SPEC.md lines 176-191

### 19. [DONE] Partial Fulfillment Payment Amount
**File:** `order/src/main/kotlin/io/ktor/foodies/order/service/OrderService.kt` lines 305-331
**Issue:** When order is partially fulfilled due to stock rejection, the total price changes, but payment processing should use the updated amount.
**Expected:** Ensure `OrderStockConfirmedEvent` includes the updated `totalAmount` after partial fulfillment.
**Priority:** Medium
**Spec Reference:** ORDER_SPEC.md lines 1406-1410 (Future Enhancement)

### 20. [DONE] Event Idempotency Key for OrderStockConfirmedEvent
**File:** Missing implementation
**Issue:** Payment service expects `eventId` field in `OrderStockConfirmedEvent` for idempotency, but Order service doesn't generate it.
**Expected:** Generate unique event ID when publishing OrderStockConfirmedEvent.
**Priority:** High
**Spec Reference:** PAYMENT_SPEC.md line 184

## Testing Gaps

### 21. Missing Integration Test for Order-Payment Event Flow
**Issue:** No end-to-end test verifying that stock confirmation triggers payment processing.
**Expected:** Create integration test that:
  1. Creates an order
  2. Confirms stock
  3. Verifies OrderStockConfirmedEvent is published
  4. Verifies Payment service processes payment
  5. Verifies Order status updates to Paid
**Priority:** Medium

### 22. [DONE] Missing Test for Payment Failure Stock Return
**Issue:** When payment fails, stock should be returned (StockReturnedEvent), but this flow is not visible in tests.
**Expected:** Test that payment failure triggers stock return event.
**Priority:** Medium
**Spec Reference:** ORDER_SPEC.md lines 374-380

## Summary

**High Priority Issues:** 0
**Medium Priority Issues:** 1
**Low Priority Issues:** 1

**Critical Path:** All high priority security issues have been resolved. The payment flow is now functional as Order service correctly publishes `OrderStockConfirmedEvent`. Port conflicts between Order and Payment services have been resolved by moving Payment to 8085 and ensuring Order uses 8084.
