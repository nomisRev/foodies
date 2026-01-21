# Security

For our application we need both `OAuth`, and `JWT` for properly authenticating, and authorizing users.
Everything related to security is located in [
`Security.kt`](../server/src/main/kotlin/io/ktor/foodies/server/Security.kt),
but we'll walk through the setup below.

## OAuth

Users need to be able to _authenticate_ i.e. using their Google account such that we can _authorize_ them to place
orders. So for users to _login_ or authenticate themselves, we need to set up `OAuth`. Typically, this is done using an
_Identity Provider_ such as Okta, Auth0, or using cloud platforms such as AWS Cognito, Azure AD, or Google Cloud
Identity.

To set up `OAuth` with Ktor we need `issuer`, `clientId`, and `clientSecret`. Check your provider for the values.
We add them to the configuration following [adding new configuration](PROJECT_SETUP.md#adding-new-configuration),
and use it to `install` the `Authentication` plugin.

Before we can actually do so, we need to _discover_ the configuration of the issuer. To retrieve the
`OpenIdConfiguration` we `get` the `/.well-known/openid-configuration` endpoint, and extract all required values. We
ignore all optional values for now so we add `@JsonIgnoreUnknownKeys`.

```kotlin
@Serializable
@JsonIgnoreUnknownKeys
data class OpenIdConfiguration(
    val issuer: String,
    @SerialName("authorization_endpoint")
    val authorizationEndpoint: String,
    @SerialName("token_endpoint")
    val tokenEndpoint: String,
    @SerialName("jwks_uri")
    val jwksUri: String
)

suspend fun HttpClient.discover(issuer: String): OpenIdConfiguration =
    get("$issuer/.well-known/openid-configuration").body()
```

Once we have the `OpenIdConfiguration`, we can use it to configure the `Authentication` plugin.
`urlProvider` defines the _callback endpoint_ (`/oauth/callback`) which is the route that will be called by the Identity
Provider after authentication. We set scopes `"openid", "profile", "email"`, and everything else is boilerplate passing
the other values, and configuring the `HttpClient` for communication with the Identity Provider.

```kotlin
fun Application.security(client: HttpClient, config: Config.Security, openIdConfig: OpenIdConfiguration) {
    authentication {
        oauth("oauth") {
            urlProvider = { "${request.origin.scheme}://${request.host()}:${request.port()}/oauth/callback" }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "foodies-oauth",
                    authorizeUrl = openIdConfig.authorizationEndpoint,
                    accessTokenUrl = openIdConfig.tokenEndpoint,
                    requestMethod = HttpMethod.Post,
                    clientId = config.clientId,
                    clientSecret = config.clientSecret,
                    defaultScopes = listOf("openid", "profile", "email"),
                )
            }

            client = httpClient
        }
    }

    routing {
        authenticate("oauth") {
            get("/login") { }
            get("/oauth/callback") { TODO() }
        }
    }
}
```

This `OAuth` provider is only going to be used by the login flow from the website this requires _session based
authorization_. Authorizing users requires storing the _id token_ in the session.
To do so safely, cookies need to be managed on the server side and only the _session-id_ should be sent to the client.

```kotlin
@Serializable
data class UserSession(val idToken: String)

fun Application.security() {
    install(Sessions) {
        cookie<UserSession>("USER_SESSION", SessionStorageMemory()) {
            cookie.secure = !this@security.developmentMode
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "lax"
        }
    }
}
```

Now that we can safely set `UserSession` with the `idToken` from the `OAuth` provider, we can implement our _callback_
endpoint. If the `idToken` is not available we return `Unauthorized`, and otherwise we set the `UserSession` and
redirect to the homepage.

```kotlin
get("/oauth/callback") {
    val idToken =
        call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()?.extraParameters["id_token"]
    if (idToken == null) {
        call.respond(Unauthorized)
    } else {
        call.sessions.set(UserSession(idToken))
        call.respondRedirect("/")
    }
}
```

With all this setup the system is secure for user interactions. For a more detailed view of the entire system's security, including service-to-service communication, see the [Hybrid Authentication Architecture](hybrid-auth-architecture.md).


