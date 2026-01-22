# Claims Management UI

## Overview

The Claims Management UI provides a web interface for viewing, creating, and managing insurance claims. Built with Angular, it offers list and detail views, filtering, pagination, and action capabilities for claim lifecycle management.

### Business Value

- **Efficient Claims Processing**: Quick access to claim information
- **Streamlined Workflows**: Actions available in context
- **Data Visibility**: Resolved actor names for readability
- **Filtering and Search**: Find claims quickly

---

## User Stories

### US-UI-001: View Claims List
**As an** insurance operator
**I want to** see a list of all claims
**So that** I can find and manage claims

**Acceptance Criteria**:
- Paginated table with key fields
- Sortable columns
- Status and type filters
- Click row to view detail

### US-UI-002: View Claim Detail
**As an** insurance operator
**I want to** see full claim details
**So that** I have all information needed

**Acceptance Criteria**:
- All claim fields displayed
- Policyholder and insurer names resolved
- Expert assignments listed
- Comments and history visible

### US-UI-003: Create New Claim
**As an** insurance operator
**I want to** create a new claim
**So that** I can begin processing

**Acceptance Criteria**:
- Form with required fields
- Policyholder and insurer dropdowns
- Validation feedback
- Success redirect to detail

### US-UI-004: Update Claim Status
**As an** insurance operator
**I want to** change a claim's status
**So that** I can progress the workflow

**Acceptance Criteria**:
- Only valid transitions shown
- Confirmation before change
- Success feedback
- Page refresh with new status

### US-UI-005: Assign Expert
**As an** insurance operator
**I want to** assign an expert to a claim
**So that** assessment can begin

**Acceptance Criteria**:
- Expert selection dropdown
- Optional scheduled date
- Optional notes field
- Assignment appears in claim detail

### US-UI-006: Add Comment
**As an** insurance operator
**I want to** add comments to a claim
**So that** I can document progress

**Acceptance Criteria**:
- Text input for comment
- Author type selection
- Comment appears immediately
- Sorted by newest first

---

## Application Routes

| Route | Component | Description |
|-------|-----------|-------------|
| `/` | Redirect | Redirects to `/dashboard` |
| `/dashboard` | DashboardPageComponent | Dashboard view |
| `/claims` | ClaimsListPageComponent | Claims list |
| `/claims/new` | ClaimCreatePageComponent | Create claim form |
| `/claims/:id` | ClaimDetailPageComponent | Claim detail view |
| `/**` | Redirect | Catch-all to dashboard |

---

## Claims List Page

### Layout

```
┌─────────────────────────────────────────────────────────┐
│  Claims                                    [+ New Claim] │
├─────────────────────────────────────────────────────────┤
│  Filters: [Status ▼] [Type ▼]                           │
├─────────────────────────────────────────────────────────┤
│  Reference    │ Status    │ Type       │ Policyholder   │
│───────────────┼───────────┼────────────┼────────────────│
│  INC-2026-001 │ DECLARED  │ WATER_DMG  │ John Doe       │
│  INC-2026-002 │ QUALIFIED │ FIRE       │ Jane Smith     │
│  INC-2026-003 │ RESOLVED  │ THEFT      │ Acme Corp      │
│  ...          │           │            │                │
├─────────────────────────────────────────────────────────┤
│  << < Page 1 of 10 > >>    Showing 20 of 200 claims     │
└─────────────────────────────────────────────────────────┘
```

### Table Columns

| Column | Field | Sortable |
|--------|-------|----------|
| Reference | referenceNumber | Yes |
| Status | status (badge) | Yes |
| Type | type | Yes |
| Policyholder | policyholderName | Yes |
| Insurer | insurerName | Yes |
| Damage | estimatedDamage + currency | Yes |
| Incident Date | incidentDate | Yes |
| Created | createdAt | Yes |

### Filters

- **Status**: Dropdown with all status values
- **Type**: Dropdown with all incident types
- **Clear**: Reset all filters

### Pagination

- Page size options: 10, 20, 50
- Navigate: First, Previous, Page N, Next, Last
- Show total count

### API Call

```typescript
GET /api/portal/v1/claims?status=DECLARED&type=WATER_DAMAGE&page=0&size=20&sort=createdAt,desc
```

---

## Claim Detail Page

### Layout

```
┌─────────────────────────────────────────────────────────┐
│  ← Back to Claims                                       │
│                                                         │
│  INC-2026-000150                        [DECLARED]      │
│  Water Damage                                           │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Actions: [Update Status ▼] [Assign Expert]             │
│                                                         │
├──────────────────────────┬──────────────────────────────┤
│  Overview                │  Location                    │
│  ──────────              │  ────────                    │
│  Type: Water Damage      │  123 Main St, Paris          │
│  Date: 2026-01-20        │  48.8566, 2.3522             │
│  Damage: €5,000.00       │                              │
│  Description: Pipe burst │                              │
│  in basement...          │                              │
├──────────────────────────┴──────────────────────────────┤
│  Policyholder              Insurer                      │
│  ─────────────             ───────                      │
│  John Doe                  Acme Insurance               │
│  john@example.com          contact@acme.com             │
│  +33612345678              +33145678900                 │
├─────────────────────────────────────────────────────────┤
│  Expert Assignments                                     │
│  ┌────────────────────────────────────────────────────┐ │
│  │ Jane Expert - Scheduled: 2026-01-25 10:00          │ │
│  │ Status: PENDING - Notes: On-site assessment        │ │
│  └────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────┤
│  Comments                                   [Add +]     │
│  ┌────────────────────────────────────────────────────┐ │
│  │ 2026-01-22 10:30 - John Doe (POLICYHOLDER)         │ │
│  │ Basement is still flooded, waiting for assessment  │ │
│  └────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────┤
│  History                                                │
│  ┌────────────────────────────────────────────────────┐ │
│  │ 2026-01-22 10:30 - EXPERT_ASSIGNED                 │ │
│  │   Expert assigned: Jane Expert                     │ │
│  │                                                    │ │
│  │ 2026-01-22 09:00 - STATUS_CHANGED                  │ │
│  │   DECLARED → QUALIFIED                             │ │
│  │                                                    │ │
│  │ 2026-01-20 09:00 - INCIDENT_DECLARED               │ │
│  │   Claim created                                    │ │
│  └────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

### Sections

1. **Header**: Reference, status badge, type
2. **Actions**: Update status, assign expert buttons
3. **Overview**: Key claim details
4. **Location**: Address and map coordinates
5. **Actors**: Policyholder and insurer cards
6. **Expert Assignments**: List of assigned experts
7. **Comments**: Threaded comments
8. **History**: Event timeline

### Status Update

Only shows valid transitions:
- DECLARED: [Qualify] [Abandon]
- QUALIFIED: [Assign Expert] [Abandon]
- IN_PROGRESS: [Resolve] [Abandon]
- RESOLVED/ABANDONED: No actions

### API Calls

```typescript
// Get claim detail
GET /api/portal/v1/claims/{id}

// Update status
PUT /api/portal/v1/claims/{id}/status
{ "status": "QUALIFIED", "reason": "Documentation verified" }

// Assign expert
POST /api/portal/v1/claims/{id}/expert
{ "expertId": "...", "scheduledDate": "2026-01-25T10:00:00Z" }

// Add comment
POST /api/portal/v1/claims/{id}/comments
{ "content": "...", "authorId": "...", "authorType": "INSURER" }
```

---

## Claim Create Page

### Layout

```
┌─────────────────────────────────────────────────────────┐
│  ← Back to Claims                                       │
│                                                         │
│  Create New Claim                                       │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Policyholder *                                         │
│  [Select Policyholder ▼]                                │
│                                                         │
│  Insurer *                                              │
│  [Select Insurer ▼]                                     │
│                                                         │
│  Incident Type *                                        │
│  [Select Type ▼]                                        │
│                                                         │
│  Incident Date *                                        │
│  [Date Picker]                                          │
│                                                         │
│  Description                                            │
│  [                                                    ] │
│  [                                                    ] │
│                                                         │
│  Location                                               │
│  Address: [                                           ] │
│  Latitude: [        ]  Longitude: [        ]           │
│                                                         │
│  Estimated Damage                                       │
│  [            ] [EUR ▼]                                 │
│                                                         │
│                                    [Cancel] [Create]    │
└─────────────────────────────────────────────────────────┘
```

### Fields

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| Policyholder | Select | Yes | Must select |
| Insurer | Select | Yes | Must select |
| Type | Select | Yes | Must select |
| Incident Date | Date | Yes | Not future |
| Description | Textarea | No | Max 2000 chars |
| Address | Text | No | |
| Latitude | Number | No | -90 to 90 |
| Longitude | Number | No | -180 to 180 |
| Estimated Damage | Number | No | > 0 |
| Currency | Select | No | Default: EUR |

### Dropdowns Data

```typescript
// Policyholders
GET /api/portal/v1/policyholders

// Insurers
GET /api/portal/v1/insurers

// Types (client-side constant)
['WATER_DAMAGE', 'FIRE', 'THEFT', 'LIABILITY',
 'PROPERTY_DAMAGE', 'NATURAL_DISASTER', 'VEHICLE_ACCIDENT', 'OTHER']
```

### On Submit

```typescript
POST /api/portal/v1/claims
{
  "policyholderId": "...",
  "insurerId": "...",
  "type": "WATER_DAMAGE",
  "incidentDate": "2026-01-20T08:30:00Z",
  "description": "...",
  "location": { "address": "...", "latitude": 48.8566, "longitude": 2.3522 },
  "estimatedDamage": 5000.00,
  "currency": "EUR"
}
```

On success: Navigate to `/claims/{newId}`

---

## Data Models

### ClaimSummary (List)

```typescript
interface ClaimSummary {
  id: string;
  referenceNumber: string;
  status: ClaimStatus;
  type: ClaimType;
  policyholderName: string;
  insurerName: string;
  estimatedDamage?: number;
  currency?: string;
  incidentDate: string;
  createdAt: string;
}
```

### ClaimDetail

```typescript
interface ClaimDetail {
  id: string;
  referenceNumber: string;
  status: ClaimStatus;
  availableTransitions: ClaimStatus[];
  type: ClaimType;
  description?: string;
  incidentDate: string;
  estimatedDamage?: number;
  currency?: string;
  location?: Location;
  policyholder: Actor;
  insurer: Actor;
  expertAssignments: ExpertAssignment[];
  comments: Comment[];
  history: Event[];
  createdAt: string;
  updatedAt: string;
}
```

### Supporting Types

```typescript
type ClaimStatus = 'DECLARED' | 'QUALIFIED' | 'IN_PROGRESS' | 'RESOLVED' | 'ABANDONED';
type ClaimType = 'WATER_DAMAGE' | 'FIRE' | 'THEFT' | 'LIABILITY' |
                 'PROPERTY_DAMAGE' | 'NATURAL_DISASTER' | 'VEHICLE_ACCIDENT' | 'OTHER';

interface Actor {
  id: string;
  name: string;
  email?: string;
  phone?: string;
}

interface ExpertAssignment {
  id: string;
  expert: Actor;
  scheduledDate?: string;
  status: string;
  notes?: string;
  assignedAt: string;
}

interface Comment {
  id: string;
  content: string;
  authorId: string;
  authorType: string;
  authorName?: string;
  createdAt: string;
}

interface Event {
  id: string;
  eventType: string;
  description?: string;
  oldValue?: string;
  newValue?: string;
  actorName?: string;
  occurredAt: string;
}
```

---

## Shared Components

### Status Badge

```typescript
const CLAIM_STATUS_COLORS: Record<ClaimStatus, string> = {
  DECLARED: 'gray',
  QUALIFIED: 'blue',
  IN_PROGRESS: 'yellow',
  RESOLVED: 'green',
  ABANDONED: 'red'
};
```

### Actor Card

Displays actor information with icon, name, email, phone.

### Event Timeline

Displays history events in chronological order with icons per event type.

---

## Services

### ClaimsService

```typescript
@Injectable({ providedIn: 'root' })
export class ClaimsService {
  // Signals
  claims$ = signal<Page<ClaimSummary> | null>(null);
  currentClaim$ = signal<ClaimDetail | null>(null);
  loading$ = signal<boolean>(false);

  // Methods
  loadClaims(filter, page, size, sort): Observable<Page<ClaimSummary>>;
  getClaimById(id: string): Observable<ClaimDetail>;
  createClaim(request: CreateClaimRequest): Observable<ClaimDetail>;
  updateStatus(id: string, request: StatusUpdateRequest): Observable<ClaimDetail>;
  assignExpert(id: string, request: ExpertAssignmentRequest): Observable<void>;
  addComment(id: string, request: CommentRequest): Observable<Comment>;
  getHistory(id: string): Observable<Event[]>;
}
```

---

## Error Handling

### Validation Errors

Display inline error messages below invalid fields.

### API Errors

| Code | Display |
|------|---------|
| 400 | Show validation message |
| 404 | "Claim not found" |
| 409 | Show conflict message |
| 500 | "An error occurred. Please try again." |

### Loading States

- Skeleton loaders for list and detail
- Disabled buttons during submit
- Loading indicator for actions

---

## Testing

### Access Application

```bash
# Start frontend
cd frontend/portal
ng serve

# Navigate to
http://localhost:4200/claims
```

### Create Test Claim

1. Navigate to Claims list
2. Click "New Claim" button
3. Fill required fields
4. Submit form
5. Verify redirect to detail page

---

## Related Documentation

- [PRD.md](../PRD.md) - Product requirements (FR-POR-002, FR-POR-003)
- [portal-dashboard.md](portal-dashboard.md) - Dashboard page
- [incident-lifecycle.md](incident-lifecycle.md) - Status transitions
- [expert-assignment.md](expert-assignment.md) - Expert assignment flow
