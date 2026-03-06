# Routes and Validation

Keep transport concerns in routes and business decisions in services.

## Route style

- Define route groups as `Route` extension functions (`fun Route.menuRoutes(...)`).
- Group endpoints with `route("/resource")`.
- Parse typed path/query params using shared syntax helpers (`io.ktor.foodies.server.getValue`).
- Decode JSON bodies with `call.receive<RequestType>()`.
- Convert domain objects to response DTOs in route layer.

## Response mapping

- Return explicit status codes at the route layer.
- Use nullable or sealed results from the service layer for expected outcomes.
- Avoid using exceptions for normal branches such as not found or forbidden.

Example pattern:

```kotlin
when (val result = orderService.getOrder(id, principal.userId)) {
    is GetOrderResult.Success -> call.respond(result.order)
    is GetOrderResult.NotFound -> call.respond(HttpStatusCode.NotFound, "Order not found")
    is GetOrderResult.Forbidden -> call.respond(HttpStatusCode.Forbidden, "Access denied to order")
}
```

## Validation pattern

- Validate request/domain inputs with shared `validate { ... }` DSL from `server-shared`.
- Throw `ValidationException` for invalid input collected by validation rules.
- Handle `ValidationException` in `StatusPages` and map to `400 Bad Request`.

Pattern:

```kotlin
install(StatusPages) {
    exception<ValidationException> { call, cause ->
        call.respondText(cause.message, status = HttpStatusCode.BadRequest)
    }
}
```

## DTO and domain boundaries

- Keep request/response DTOs separate from persistence models.
- Keep domain models and sealed outcomes in `Domain.kt` or service-level types.
- Keep repository details (Exposed table/result mapping) hidden from route handlers.

## Error handling guidelines

- Keep exception handlers explicit in `StatusPages`.
- Return stable, predictable client errors for validation and malformed input.
- Do not leak secrets, credentials, or internal stack details in error responses or logs.
