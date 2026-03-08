# Consolidated Issues

Validated on 2026-03-06.

Merged from:
- `basket/ISSUES.md`
- `menu/ISSUES.md`
- `order/ISSUES.md`
- `payment/ISSUES.md`
- `profile/ISSUES.md`
- `webapp/ISSUES.md`
- `.worktree/compose-web/website/ISSUES.md`

## Confirmed Issues

### P0

1. RabbitMQ exchange type conflict on shared `foodies` exchange.
- `order` declares `x-delayed-message` in `order/src/main/kotlin/io/ktor/foodies/order/fulfillment/FulfillmentEventConsumer.kt:44`.
- `menu` and `payment` declare the same exchange as `topic` in `menu/src/main/kotlin/io/ktor/foodies/menu/MenuModule.kt:52` and `payment/src/main/kotlin/io/ktor/foodies/payment/PaymentModule.kt:50`.
- Defaults point to the same exchange name (`foodies`) in `order/src/main/resources/application.yaml:22`, `menu/src/main/resources/application.yaml:13`, `payment/src/main/resources/application.yaml:14`.
- Impact: startup-order-dependent `PRECONDITION_FAILED`.

2. JWT validation callback constructs `UserPrincipal` but does not return it.
- In `server-shared/src/main/kotlin/io/ktor/foodies/server/openid/Security.kt:40-52`, `validate { ... }` assigns `val principal = ...` but does not return `principal`.
- Secure routes require `call.principal<UserPrincipal>()` in `server-shared/src/main/kotlin/io/ktor/foodies/server/auth/SecureRouting.kt:27`.
- Impact: principal wiring can break at runtime despite token verification.

### P1

3. Partial stock rejection can still move order to `StockConfirmed` without reservation.
- Menu stock reservation is all-or-nothing in `menu/src/main/kotlin/io/ktor/foodies/menu/stock/ExposedStockRepository.kt:54-63`.
- Order service accepts partial quantities and moves to `StockConfirmed` in `order/src/main/kotlin/io/ktor/foodies/order/fulfillment/FulfillmentService.kt:178-221`.
- Impact: payment can proceed for quantities not reserved in menu stock.

4. Order creation idempotency is non-atomic and can return server error under concurrency.
- Pre-check before insert in `order/src/main/kotlin/io/ktor/foodies/order/placement/PlacementService.kt:38`.
- Insert happens later in `order/src/main/kotlin/io/ktor/foodies/order/placement/ExposedPlacementRepository.kt:46-62`.
- Unique DB constraint exists on `request_id` in `order/src/main/kotlin/io/ktor/foodies/order/persistence/OrderTables.kt:25`.
- DB uniqueness exceptions are not explicitly mapped in `order/src/main/kotlin/io/ktor/foodies/order/OrderApp.kt:40-47`.

5. `PUT /orders/{id}/cancel` accepts malformed JSON and still cancels.
- Body parse failures are swallowed and default reason is used in `order/src/main/kotlin/io/ktor/foodies/order/tracking/TrackingRoutes.kt:52-53`.
- Impact: invalid payloads still execute a state-changing operation.

6. Admin listing endpoint reads filters from path parameters, not query parameters.
- `order/src/main/kotlin/io/ktor/foodies/order/admin/AdminRoutes.kt:18-23` uses `call.parameters[...]`.
- Query-form requests (`/admin/orders?offset=...`) are ignored.

7. Idempotency contract for cancellation/shipping is incomplete.
- Header required in routes (`TrackingRoutes` and `AdminRoutes`) but service-level `requestId` is unused in `order/src/main/kotlin/io/ktor/foodies/order/tracking/TrackingService.kt:38` and `order/src/main/kotlin/io/ktor/foodies/order/fulfillment/FulfillmentService.kt:58`.

8. Webapp checkout path is not wired.
- Cart UI links to `/checkout` in `webapp/src/main/kotlin/io/ktor/foodies/webapp/basket/BasketRoutes.kt:400` and `:448`.
- No `/checkout` route is defined in webapp routes.
- Impact: cart flow cannot complete checkout from webapp.

### P2

9. Cancel endpoint returns `400` for not-found/forbidden outcomes.
- Domain outcomes become `IllegalArgumentException` in `order/src/main/kotlin/io/ktor/foodies/order/tracking/TrackingService.kt:41-43`.
- Global `StatusPages` maps `IllegalArgumentException` to `400` in `order/src/main/kotlin/io/ktor/foodies/order/OrderApp.kt:44-45`.
- `GET /orders/{id}` already distinguishes `404` and `403`.

10. `currency` has DB length limit but no request validation.
- DB: `varchar("currency", 3)` in `order/src/main/kotlin/io/ktor/foodies/order/persistence/OrderTables.kt:31`.
- Request uses `request.currency` without validating length/format in `order/src/main/kotlin/io/ktor/foodies/order/placement/PlacementService.kt:60` and `:85-97`.

11. Missing route test coverage for admin query parsing.
- `order/src/test/kotlin/io/ktor/foodies/order/admin/AdminRoutesSpec.kt:15-45` checks auth status only, not pagination/filter query behavior.

### P3

12. `BasketClient` contract includes parameters ignored by HTTP implementation.
- Signature includes `buyerId` and `token` in `order/src/main/kotlin/io/ktor/foodies/order/placement/BasketClient.kt:27`.
- `HttpBasketClient.getBasket` ignores both in `order/src/main/kotlin/io/ktor/foodies/order/placement/BasketClient.kt:36-39`.

13. Notification path mixes structured logger with `println` and includes user data in free-form string.
- `order/src/main/kotlin/io/ktor/foodies/order/fulfillment/FulfillmentNotificationService.kt:15-17`.

14. Exception-driven control flow is used for expected domain outcomes.
- Multiple `IllegalArgumentException` branches in tracking/placement/fulfillment services and generic 400 mapping in `OrderApp`.
- This conflicts with repository guidance to model expected outcomes with explicit types.

15. `order` tests are heavily in-memory and do not validate real integration wiring.
- In-memory repository/client/publishers in `order/src/test/kotlin/io/ktor/foodies/order/TestSyntax.kt`.
- Risk: DB migration/RabbitMQ/serialization regressions can slip through.

16. `order/README.md` is out of sync with runtime behavior.
- Example mismatches:
- `AUTH_AUDIENCE` default documented as `order-service` but runtime default is `foodies`.
- Health endpoints documented as `/healthz` and `/healthz/ready`, runtime exposes `/healthz/startup`, `/healthz/liveness`, `/healthz/readiness`.
