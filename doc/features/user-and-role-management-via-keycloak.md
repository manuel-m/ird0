# Feature: User and Role Management via Keycloak

## Context

The application is a full-stack system composed of:

* A Java backend
* An Angular frontend
* Keycloak for authentication and authorization
* PostgreSQL as the main database
* HashiCorp Vault for secret management

Authentication is already implemented using Keycloak (OIDC).
User identities are now fully managed by Keycloak instead of a local JSON file.

---

## Objective

Enable system administrators to manage portal users and their roles **without developing custom administration pages**, by leveraging Keycloak’s built-in capabilities.

---

## Scope

This feature introduces a **centralized user and role management strategy based on Keycloak**, covering:

* User lifecycle management (create, enable/disable, delete)
* Role assignment and revocation
* Secure role propagation to backend and frontend
* Role-based access control in the application

---

## Functional Requirements

1. **User Management**

    * Users are created and managed in Keycloak
    * No user data is stored locally in JSON files
    * User identity is resolved via JWT tokens issued by Keycloak

2. **Role Management**

    * Roles are defined as Keycloak *client roles* (e.g. `portal-admin`, `portal-user`)
    * Roles are included in the access token
    * Users can be assigned one or multiple roles via Keycloak

3. **Administrator Access**

    * Administrators manage users and roles through the Keycloak Admin Console
    * No custom Angular administration UI is required at this stage
    * Only users with Keycloak admin privileges can access the admin console

4. **Backend Authorization**

    * The Java backend enforces authorization using roles from the JWT token
    * Endpoints are protected using role-based annotations (e.g. `@PreAuthorize`)
    * The backend does not call Keycloak Admin APIs for this feature

5. **Frontend Authorization**

    * Angular uses Keycloak roles to:

        * Protect routes
        * Show or hide UI elements
    * Authorization logic is derived from the decoded access token

---

## Non-Goals

* No custom user administration UI in Angular
* No duplication of user data in the application database
* No business-specific role logic beyond RBAC

---

## Security Considerations

* Keycloak admin credentials (if ever needed) must be stored in Vault
* Access tokens must be validated on every backend request
* Roles must not be trusted client-side only; backend enforcement is mandatory

---

### Future Extensions (Out of Scope)

* Custom admin UI using Keycloak Admin REST API
* Advanced permission models (ABAC)
* User self-service management beyond Keycloak defaults

