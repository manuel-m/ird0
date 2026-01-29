# User and Role Management via Keycloak

## Overview

The User and Role Management feature enables system administrators to manage portal users and their permissions entirely through Keycloak's built-in Admin Console. The application delegates all user lifecycle operations to Keycloak, treating it as the single source of truth for identity and access control.

### Business Value

- **No Custom Admin UI**: Leverage Keycloak's mature, tested admin interface instead of building custom administration pages
- **Centralized Identity Management**: All user data lives in Keycloak, not duplicated in application databases
- **Consistent RBAC**: Role definitions and assignments flow directly from Keycloak to application authorization
- **Operational Simplicity**: Administrators use a single tool (Keycloak Admin Console) for all user management tasks
- **Security by Default**: Keycloak provides battle-tested user management with audit logging, password policies, and session management

---

## User Stories

### US-MGMT-001: Admin Creates New Portal User
**As a** system administrator
**I want to** create new user accounts via Keycloak Admin Console
**So that** new employees can access the insurance portal

**Acceptance Criteria**:
- Administrator logs into Keycloak Admin Console with admin credentials
- New user created in the `ird0` realm with username, email, and temporary password
- User marked as "Email Verified" (internal users)
- User receives temporary password and must change on first login
- No user data is stored in the application database

### US-MGMT-002: Admin Assigns Roles to User
**As a** system administrator
**I want to** assign roles to users via Keycloak Admin Console
**So that** users have appropriate access levels in the portal

**Acceptance Criteria**:
- Administrator navigates to user's Role Mappings in Keycloak
- Client roles from `ird0-portal-bff` are available for assignment
- Assigning `claims-manager` automatically includes `claims-viewer` permissions (composite role)
- Assigning `claims-admin` automatically includes all lower permissions
- Role changes take effect on the user's next token refresh (within 5 minutes)

### US-MGMT-003: Admin Disables User Account
**As a** system administrator
**I want to** disable a user account without deleting it
**So that** former employees cannot access the portal while preserving audit history

**Acceptance Criteria**:
- Administrator sets user's "Enabled" toggle to OFF in Keycloak
- Disabled user cannot log in (Keycloak rejects authentication)
- Existing sessions are terminated
- User data and role assignments are preserved for audit purposes
- Account can be re-enabled if needed

### US-MGMT-004: Admin Revokes User Roles
**As a** system administrator
**I want to** remove roles from a user
**So that** their access level can be adjusted as responsibilities change

**Acceptance Criteria**:
- Administrator navigates to user's Role Mappings in Keycloak
- Assigned client roles can be removed
- Removing `claims-manager` also removes its permissions but not `claims-viewer` if explicitly assigned
- Role changes take effect on the user's next token refresh

### US-MGMT-005: Admin Deletes User Account
**As a** system administrator
**I want to** permanently delete a user account
**So that** the account is completely removed from the system

**Acceptance Criteria**:
- Administrator uses "Delete" action on user in Keycloak Admin Console
- All user data, sessions, and role assignments are permanently removed
- Action is irreversible
- Use disable (US-MGMT-003) when audit trail preservation is required

### US-MGMT-006: Admin Resets User Password
**As a** system administrator
**I want to** reset a user's password
**So that** users who forget their password can regain access

**Acceptance Criteria**:
- Administrator navigates to user's Credentials tab in Keycloak
- New temporary password can be set
- "Temporary" flag forces password change on next login
- Email notification can optionally be sent (if email configured)

---

## Keycloak Admin Console Operations

### Accessing the Admin Console

| Setting | Value |
|---------|-------|
| URL | `http://localhost:8180/admin` |
| Realm | `ird0` |
| Default Admin | `admin` / `admin` (development only) |

**Production Note**: Admin credentials must be stored in HashiCorp Vault and rotated regularly.

### User Management Operations

#### Creating a New User

```
┌─────────────────────────────────────────────────────────────────┐
│  Keycloak Admin Console                                          │
│  Realm: ird0 → Users → Add user                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Username*:        [john.doe                    ]               │
│  Email:            [john.doe@company.com        ]               │
│  First Name:       [John                        ]               │
│  Last Name:        [Doe                         ]               │
│  Email Verified:   [✓]                                          │
│  Enabled:          [✓]                                          │
│                                                                  │
│  [Create]                                                        │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Steps**:
1. Navigate to **Users** in left menu
2. Click **Add user**
3. Fill in required fields (username is mandatory)
4. Enable **Email Verified** for internal users
5. Ensure **Enabled** is checked
6. Click **Create**
7. Navigate to **Credentials** tab to set initial password

#### Setting User Credentials

```
┌─────────────────────────────────────────────────────────────────┐
│  Keycloak Admin Console                                          │
│  Users → john.doe → Credentials                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Set password                                                    │
│                                                                  │
│  Password*:         [••••••••••                 ]               │
│  Password confirm*: [••••••••••                 ]               │
│  Temporary:         [✓] User must change password on next login │
│                                                                  │
│  [Set password]                                                  │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

#### Assigning Roles to User

```
┌─────────────────────────────────────────────────────────────────┐
│  Keycloak Admin Console                                          │
│  Users → john.doe → Role mapping                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Filter by clients: [ird0-portal-bff         ▼]                 │
│                                                                  │
│  Available Roles              Assigned Roles                     │
│  ┌──────────────────┐        ┌──────────────────┐               │
│  │ claims-admin     │   →    │ claims-manager   │               │
│  │                  │   ←    │                  │               │
│  └──────────────────┘        └──────────────────┘               │
│                                                                  │
│  Effective Roles (including composite):                          │
│  • claims-manager                                                │
│  • claims-viewer (inherited from claims-manager)                │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Steps**:
1. Navigate to **Users** → select user → **Role mapping**
2. Click **Assign role**
3. Filter by client: `ird0-portal-bff`
4. Select the appropriate role(s)
5. Click **Assign**

#### Disabling/Enabling a User

```
┌─────────────────────────────────────────────────────────────────┐
│  Keycloak Admin Console                                          │
│  Users → john.doe → Details                                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  User enabled:  [ ] ← Toggle OFF to disable                     │
│                                                                  │
│  [Save]                                                          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Role Configuration

Roles are defined as **client roles** on the `ird0-portal-bff` client. See [user-authentification.refined.md](user-authentification.refined.md#client-roles-on-ird0-portal-bff) for the complete role specification.

#### Role Hierarchy (Composite Roles)

```
claims-admin
  └── claims-manager (composite)
        └── claims-viewer (composite)
```

| Role | Permissions | Includes |
|------|-------------|----------|
| `claims-viewer` | Read-only access to claims, dashboard, actors | — |
| `claims-manager` | Create claims, update status, assign experts, add comments | `claims-viewer` |
| `claims-admin` | Full access including future administrative operations | `claims-manager` |

**Configuration in Keycloak**: Each higher role is configured as a composite role that includes the lower role(s). This is set up in **Clients** → `ird0-portal-bff` → **Roles** → select role → **Associated roles**.

---

## Administrative Workflows

### New User Onboarding Flow

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│   HR/Manager │       │   Keycloak   │       │   New User   │
│              │       │    Admin     │       │              │
└──────┬───────┘       └──────┬───────┘       └──────┬───────┘
       │                       │                       │
       │  1. Request access    │                       │
       │   (ticket/email)      │                       │
       │──────────────────────►│                       │
       │                       │                       │
       │                       │  2. Create user in    │
       │                       │     Keycloak          │
       │                       │                       │
       │                       │  3. Assign role(s)    │
       │                       │     based on job      │
       │                       │                       │
       │                       │  4. Set temporary     │
       │                       │     password          │
       │                       │                       │
       │                       │  5. Send credentials  │
       │                       │──────────────────────►│
       │                       │                       │
       │                       │                       │  6. First login
       │                       │                       │     to portal
       │                       │                       │
       │                       │  7. Keycloak forces   │
       │                       │◄──────────────────────│
       │                       │     password change   │
       │                       │                       │
       │                       │                       │  8. Set new
       │                       │                       │     password
       │                       │                       │
       │                       │                       │  9. Access
       │                       │                       │     granted
```

### Role Change Flow

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│   Manager    │       │   Keycloak   │       │    User      │
│              │       │    Admin     │       │              │
└──────┬───────┘       └──────┬───────┘       └──────┬───────┘
       │                       │                       │
       │  1. Request role      │                       │
       │     change            │                       │
       │──────────────────────►│                       │
       │                       │                       │
       │                       │  2. Update role       │
       │                       │     mappings          │
       │                       │                       │
       │                       │  3. Confirm change    │
       │◄──────────────────────│                       │
       │                       │                       │
       │                       │                       │  4. Continue
       │                       │                       │     working
       │                       │                       │
       │                       │                       │  5. Token
       │                       │                       │     refresh
       │                       │                       │     (≤5 min)
       │                       │                       │
       │                       │                       │  6. New role
       │                       │                       │     active
```

**Note**: Role changes take effect on the next token refresh. Access token lifetime is 5 minutes (configured in Keycloak).

### User Offboarding Flow

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│     HR       │       │   Keycloak   │       │   Keycloak   │
│              │       │    Admin     │       │    Server    │
└──────┬───────┘       └──────┬───────┘       └──────┬───────┘
       │                       │                       │
       │  1. Termination       │                       │
       │     notice            │                       │
       │──────────────────────►│                       │
       │                       │                       │
       │                       │  2. Disable user      │
       │                       │     account           │
       │                       │──────────────────────►│
       │                       │                       │
       │                       │                       │  3. Terminate
       │                       │                       │     active
       │                       │                       │     sessions
       │                       │                       │
       │                       │  4. User cannot       │
       │                       │     log in            │
       │                       │                       │
       │                       │  [Optional: Delete    │
       │                       │   after retention     │
       │                       │   period]             │
```

**Best Practice**: Disable accounts rather than delete them to preserve audit history. Delete only after the required retention period.

---

## Integration with Application

### Role Propagation to JWT

When a user authenticates, Keycloak includes their assigned client roles in the access token:

```json
{
  "sub": "f8e7d6c5-b4a3-2918-7654-321098fedcba",
  "name": "John Doe",
  "email": "john.doe@company.com",
  "resource_access": {
    "ird0-portal-bff": {
      "roles": ["claims-manager", "claims-viewer"]
    }
  },
  "exp": 1706540000
}
```

The composite role configuration means `claims-viewer` appears explicitly because `claims-manager` includes it.

### Backend Authorization

The Portal BFF extracts roles from the JWT and enforces authorization on every request. See [user-authentification.refined.md](user-authentification.refined.md#portal-bff-security-specification) for the complete security configuration, including:

- JWT validation and role extraction
- Endpoint authorization matrix
- `@PreAuthorize` annotations
- 401/403 response handling

### Frontend Role Checks

The Angular SPA uses roles for UI element visibility (cosmetic only). See [user-authentification.refined.md](user-authentification.refined.md#angular-spa-specification) for:

- `AuthService.hasRole()` method
- Role-based UI conditional rendering
- Auth guard implementation

**Important**: Frontend role checks are cosmetic only. All authorization is enforced server-side at the BFF.

---

## Constraints

### No Custom Admin UI

The application does not include custom user administration pages. Rationale:
- Keycloak Admin Console is feature-complete and battle-tested
- Building custom admin UI duplicates effort and introduces security risk
- Future versions may expose Keycloak Admin REST API if self-service is needed

### No User Data in Application Database

User identities exist only in Keycloak:
- Application tables do not store user credentials, profiles, or role assignments
- User references in business data (e.g., "created by") use Keycloak user IDs (UUIDs)
- Name resolution for display purposes queries Keycloak or caches user info from tokens

### Keycloak as Single Source of Truth

All identity and access control flows through Keycloak:
- Authentication: Keycloak issues tokens
- Authorization: Roles defined and assigned in Keycloak
- User lifecycle: Create, disable, delete in Keycloak
- Audit: Keycloak event logs track user activity

### Admin Credential Security

- Keycloak admin credentials stored in HashiCorp Vault (production)
- Admin accounts use strong passwords and MFA (when available)
- Admin access logged in Keycloak event logs
- Development default (`admin/admin`) must never be used in production

---

## Test Users

For development and testing, the realm import creates these users:

| Username | Password | Role | Use Case |
|----------|----------|------|----------|
| `viewer` | `viewer` | `claims-viewer` | Read-only testing |
| `manager` | `manager` | `claims-manager` | Claims operations testing |
| `admin` | `admin` | `claims-admin` | Full access testing |

These users are created via the `keycloak/realm-export.json` file imported at Keycloak startup.

---

## Future Extensions

### Custom Admin UI via Keycloak Admin REST API

If self-service or delegated administration is required:
- Keycloak provides a complete [Admin REST API](https://www.keycloak.org/docs-api/latest/rest-api/index.html)
- Angular admin module could call this API (via BFF proxy)
- Requires `realm-admin` or scoped admin roles

### Self-Service User Management

Allow users to manage their own profile:
- Keycloak Account Console: `http://localhost:8180/realms/ird0/account`
- Users can update profile, change password, manage sessions
- No application changes required

### Advanced Permission Models (ABAC)

For attribute-based access control:
- Keycloak Authorization Services provide policy-based access
- Policies can consider user attributes, resource attributes, context
- Evaluation done at authorization time, not just authentication

### Multi-Factor Authentication

Enhance security with MFA:
- Keycloak supports TOTP, WebAuthn, and more
- Configure in Keycloak Authentication flows
- No application changes required

---

## Related Documentation

- [user-authentification.refined.md](user-authentification.refined.md) — Authentication flow, JWT handling, backend/frontend authorization
- [PRD.md](../PRD.md) — Product requirements
- [ARCHITECTURE.md](../ARCHITECTURE.md) — System architecture and security boundaries
- [Keycloak Admin Console Guide](https://www.keycloak.org/docs/latest/server_admin/) — Official Keycloak documentation
