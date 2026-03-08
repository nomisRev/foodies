# P1.5 Plan: Cancel Endpoint Accepts Malformed JSON

## Investigation Summary

Issue `P1.5` in `ISSUE.md` is reproducible in the `order` service.

Current route implementation in `order/src/main/kotlin/io/ktor/foodies/order/tracking/TrackingRoutes.kt` swallows request body parsing failures:

- `call.receive<CancelOrderRequest>()` is wrapped in `runCatching { ... }.getOrNull()`
- when parsing fails, the code falls back to `"Cancelled by user"`
- the route still invokes `trackingService.cancelOrder(...)` and performs a state-changing operation

This violates API contract expectations for malformed input and allows cancellation with invalid payloads.

## Reproducers

Added route-level reproducers in:

- `order/src/test/kotlin/io/ktor/foodies/order/tracking/TrackingRoutesSpec.kt`

Cases:

1. `PUT /orders/{id}/cancel` with malformed JSON syntax
2. `PUT /orders/{id}/cancel` with missing required `reason` field

Expected behavior in both:

- `400 Bad Request`
- no order state transition
- no cancellation/status-change event emission

Current observed behavior (failing tests):

- response is `200 OK`
- order cancellation proceeds

Validation command:

```bash
./gradlew :order:test
```

## Solution Plan

1. Update cancellation request parsing in `TrackingRoutes`:
- remove silent fallback behavior for parse failures
- require a valid `CancelOrderRequest` payload before invoking `cancelOrder`
- map deserialization/validation failures to `400 Bad Request` with a clear error message

2. Keep domain behavior unchanged:
- cancellation should still require `X-Request-Id`
- authorized user and order ownership checks remain in `TrackingService`
- idempotent behavior for already-cancelled orders remains unchanged

3. Harden contract tests:
- keep the two malformed-payload route tests as regression guards
- add one valid-payload route test asserting `200 OK` and successful cancellation to confirm no regression for happy path

4. Verify and close:
- run `./gradlew :order:test`
- confirm new and existing `order` tests pass
- update `ISSUE.md` `P1.5` status after fix is merged
