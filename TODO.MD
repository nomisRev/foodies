# TODO

- [x] Implement `POST /orders` (Order Creation from Basket) as defined in [specs/ORDER_SPEC.md](specs/ORDER_SPEC.md)
- [x] Implement `GET /orders` (List user's orders) as defined in [specs/ORDER_SPEC.md](specs/ORDER_SPEC.md)
- [x] Implement `GET /orders/{orderId}` (Get order details) as defined in [specs/ORDER_SPEC.md](specs/ORDER_SPEC.md)
- [x] Implement `PUT /orders/{orderId}/cancel` (Cancel order) as defined in [specs/ORDER_SPEC.md](specs/ORDER_SPEC.md)
- [x] Implement `GET /orders/card-types` (Get supported card types) as defined in [specs/ORDER_SPEC.md](specs/ORDER_SPEC.md)
- [x] Implement `PUT /admin/orders/{orderId}/ship` (Ship order (admin)) as defined in [specs/ORDER_SPEC.md](specs/ORDER_SPEC.md)
    - [x] Add `OrderStatusChangedEvent` to domain and publisher
    - [x] Add `shipOrder` to `OrderService` and `OrderRepository`
    - [x] Implement `PUT /admin/orders/{orderId}/ship` in `AdminRoutes.kt`
    - [x] Register `AdminRoutes` in `App.kt`
- [x] Implement `GET /admin/orders` (List all orders (admin view with filtering)) as defined in [specs/ORDER_SPEC.md](specs/ORDER_SPEC.md)
- [x] Implement `GET /healthz/ready` (Readiness check) as defined in [specs/ORDER_SPEC.md](specs/ORDER_SPEC.md)
- [x] Implement `StockConfirmedEvent` and `StockRejectedEvent` handlers as defined in [specs/ORDER_SPEC.md](specs/ORDER_SPEC.md)
- [x] Implement `OrderAwaitingValidationEvent` emission as defined in [specs/ORDER_SPEC.md](specs/ORDER_SPEC.md)
- [x] Implement `PaymentSucceededEvent` handling as defined in [specs/ORDER_SPEC.md](specs/ORDER_SPEC.md)
- [ ] Implement payment details validation (CVV, expiry, card number length) in `OrderService`
