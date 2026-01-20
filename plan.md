### Plan: Proper Authentication and Authorization Implementation

1. **Review and Update Issue Tracker**
    - [x] Create new task `bd-menu-auth`: "Add authentication and authorization to Menu service"
    - [x] Update `bd-3vn` (Add user authentication to Basket service) status if needed
    - [ ] Review `bd-1he` (Add user authentication to WebApp)

2. **Keycloak Configuration Updates**
    - [x] Update `keycloak/realm.json` to include `menu` client scope (already there, verified)
    - [x] Add `menu:read` and `menu:write` scopes (already there, verified)
    - [x] Ensure `order-service` and `basket-service` have appropriate scopes for menu access (verified)

3. **Implement Authentication in Menu Service**
    - [x] Update `menu/src/main/kotlin/io/ktor/foodies/menu/Config.kt` with `Auth` configuration (DONE)
    - [x] Update `menu/src/main/kotlin/io/ktor/foodies/menu/MenuModule.kt` to initialize security context (DONE)
    - [x] Update `menu/src/main/kotlin/io/ktor/foodies/menu/MenuApp.kt` to install `security` plugin (DONE)
    - [x] Update `menu/src/main/kotlin/io/ktor/foodies/menu/Routes.kt` to:
        - Make `GET` routes accessible (public)
        - Protect `POST`, `PUT`, `DELETE` with `admin` role or `menu:write` scope

4. **Enhance Audit Trail (User Token Flow)**
    - [x] Update `AutoAuthPlugin` in `server-shared` to propagate user token (Verified, already implemented)
    - [x] Update `UserPrincipal` to support easy audit logging (DONE: added roles and scopes)

5. **Validation and Testing**
    - [x] Update `menu/src/test/kotlin/io/ktor/foodies/menu/MenuContractSpec.kt` to include authentication in tests (DONE)
    - [x] Add new test cases for unauthorized access to Menu service (DONE)
    - [x] Verify existing tests pass with authentication (DONE)

6. **Finalize**
    - [x] Commit all changes with a descriptive message
    - [x] Close `bd-menu-auth` in the issue tracker
