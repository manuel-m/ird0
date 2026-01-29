Feature: User authentication and authorization through a BFF using Keycloak

The application uses an Angular Single Page Application (SPA) combined with
a Backend-for-Frontend (BFF) architecture.

Keycloak is used as the Identity Provider (IdP) with OpenID Connect.
The frontend never communicates directly with internal microservices.
All user authentication and authorization decisions are enforced at the BFF level.

Internal microservices run in a trusted network zone and are accessed only
by the BFF using service-level authentication.

  ---------------------------------------------------------------------------

Background:
Given a Keycloak realm is configured
And an Angular SPA is registered as a public client
And a BFF API is registered as a confidential client
And internal microservices are registered as confidential clients
And authorization is based on Keycloak client roles (no realm roles)

  ---------------------------------------------------------------------------

Scenario: User authenticates via the Angular SPA
Given a user opens the Angular application
When the user is redirected to Keycloak to log in
And the user successfully authenticates
Then Keycloak issues an access token using the Authorization Code Flow with PKCE
And the access token is returned to the Angular SPA

  ---------------------------------------------------------------------------

Scenario: Angular SPA calls the BFF with a user access token
Given the user is authenticated
When the Angular SPA calls the BFF API
Then the SPA sends the access token as a Bearer token
And the BFF validates the token signature, issuer, and expiration
And the BFF extracts client roles assigned to the BFF client
And the BFF authorizes the request based on those roles

  ---------------------------------------------------------------------------

Scenario: Authorization is enforced at the BFF level
Given the access token contains client roles for the BFF client
When the user accesses a protected BFF endpoint
Then the BFF allows the request only if the required client role is present
And no authorization decision is delegated to the frontend

  ---------------------------------------------------------------------------

Scenario: BFF calls internal microservices using service credentials
Given the BFF needs to access an internal microservice
When the BFF calls the microservice
Then the BFF uses service-level authentication (client credentials flow)
And no user access token is forwarded to the microservice
And the microservice validates that the caller is the BFF client

  ---------------------------------------------------------------------------

Scenario: Internal microservices do not depend on user identity
Given a microservice receives a request
When the request is authenticated
Then the microservice authorizes the request based on the calling service identity
And the microservice does not inspect or rely on end-user roles or identity

  ---------------------------------------------------------------------------

Scenario: Frontend role awareness is limited
Given the Angular SPA has access to the user token
When rendering the UI
Then the SPA may conditionally display UI elements based on roles
But the SPA must not be considered a security boundary

  ---------------------------------------------------------------------------

Constraints:
- Client roles are the primary mechanism for authorization
- Realm roles, if used, must be limited to cross-cutting or non-domain-specific concerns
- User authorization decisions must be enforced only in the BFF
- User access tokens must never be propagated to internal microservices
- Service-to-service communication must be authenticated
- The solution must follow Keycloak and OAuth2 best practices
