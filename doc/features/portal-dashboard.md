# Portal Dashboard

## Overview

The Portal Dashboard provides a unified view of insurance claims operations, aggregating data from multiple backend services. It presents KPIs, status distributions, and recent activity to give operators immediate visibility into claims processing.

### Business Value

- **Operational Visibility**: Single view of all claims activity
- **Performance Monitoring**: Track key metrics in real-time
- **Quick Navigation**: Access claims directly from dashboard
- **Trend Analysis**: Status and type distributions show patterns

---

## User Stories

### US-DASH-001: View KPI Summary
**As an** insurance operator
**I want to** see key performance indicators
**So that** I understand current workload

**Acceptance Criteria**:
- Total claims count
- Pending claims (DECLARED + UNDER_REVIEW)
- In-progress claims (QUALIFIED + IN_PROGRESS)
- Claims closed this month

### US-DASH-002: View Status Distribution
**As an** insurance operator
**I want to** see claims grouped by status
**So that** I can identify bottlenecks

**Acceptance Criteria**:
- Count of claims per status
- Visual chart representation
- Click to filter claims list

### US-DASH-003: View Claims by Type
**As an** insurance operator
**I want to** see claims grouped by incident type
**So that** I can analyze claim patterns

**Acceptance Criteria**:
- Count of claims per type
- Visual chart representation
- Support all incident types

### US-DASH-004: View Recent Activity
**As an** insurance operator
**I want to** see recent claim activity
**So that** I can stay current on updates

**Acceptance Criteria**:
- Last 10 events across all claims
- Event type, description, timestamp
- Link to related claim

---

## API Endpoint

### Dashboard Data

**GET** `/api/portal/v1/dashboard`

**Response**:
```json
{
  "kpis": {
    "totalClaims": 150,
    "pendingCount": 42,
    "inProgressCount": 18,
    "closedThisMonth": 12
  },
  "statusDistribution": {
    "DECLARED": 25,
    "QUALIFIED": 17,
    "IN_PROGRESS": 18,
    "RESOLVED": 60,
    "ABANDONED": 30
  },
  "claimsByType": {
    "WATER_DAMAGE": 45,
    "FIRE": 20,
    "THEFT": 25,
    "LIABILITY": 15,
    "PROPERTY_DAMAGE": 18,
    "NATURAL_DISASTER": 12,
    "VEHICLE_ACCIDENT": 10,
    "OTHER": 5
  },
  "recentActivity": [
    {
      "eventType": "INCIDENT_DECLARED",
      "description": "New claim INC-2026-000150 declared",
      "claimReference": "INC-2026-000150",
      "claimId": "550e8400-e29b-41d4-a716-446655440000",
      "occurredAt": "2026-01-22T10:30:00Z"
    },
    {
      "eventType": "EXPERT_ASSIGNED",
      "description": "Expert assigned to INC-2026-000148",
      "claimReference": "INC-2026-000148",
      "claimId": "550e8400-e29b-41d4-a716-446655440001",
      "occurredAt": "2026-01-22T10:25:00Z"
    }
  ]
}
```

---

## KPI Calculations

### Total Claims
- **Source**: Count of all incidents
- **Refresh**: On page load

### Pending Count
- **Formula**: Count(status IN ['DECLARED', 'QUALIFIED'])
- **Meaning**: Claims awaiting processing

### In Progress Count
- **Formula**: Count(status = 'IN_PROGRESS')
- **Meaning**: Claims with active expert assessment

### Closed This Month
- **Formula**: Count(status IN ['RESOLVED', 'ABANDONED'] AND updatedAt >= monthStart)
- **Meaning**: Claims completed in current month

---

## Data Flow

```
┌─────────────────────────────────────────────────────────┐
│                      Portal BFF                          │
│                                                          │
│  ┌────────────────────────────────────────────────────┐ │
│  │              DashboardController                    │ │
│  │              GET /api/portal/v1/dashboard           │ │
│  └─────────────────────┬──────────────────────────────┘ │
│                        │                                 │
│  ┌─────────────────────▼──────────────────────────────┐ │
│  │              DashboardService                       │ │
│  │  - Aggregate incident data                          │ │
│  │  - Calculate KPIs                                   │ │
│  │  - Format recent activity                           │ │
│  └─────────────────────┬──────────────────────────────┘ │
│                        │                                 │
│  ┌─────────────────────▼──────────────────────────────┐ │
│  │              IncidentClient                         │ │
│  │  GET /api/v1/incidents?size=1000                   │ │
│  └─────────────────────┬──────────────────────────────┘ │
└────────────────────────┼────────────────────────────────┘
                         │
                         ▼
              ┌─────────────────────┐
              │   Incident Service  │
              │       :8085         │
              └─────────────────────┘
```

---

## UI Components

### Dashboard Page Layout

```
┌─────────────────────────────────────────────────────────┐
│  Dashboard                                              │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌──────────┐│
│  │  Total    │ │  Pending  │ │In Progress│ │ Closed   ││
│  │   150     │ │    42     │ │    18     │ │   12     ││
│  │  claims   │ │  claims   │ │  claims   │ │this month││
│  └───────────┘ └───────────┘ └───────────┘ └──────────┘│
│                                                         │
│  ┌────────────────────────┐ ┌──────────────────────────┐│
│  │  Status Distribution   │ │  Claims by Type          ││
│  │  ┌─────────────────┐   │ │  ┌────────────────────┐  ││
│  │  │    Pie Chart    │   │ │  │    Bar Chart       │  ││
│  │  │                 │   │ │  │                    │  ││
│  │  └─────────────────┘   │ │  └────────────────────┘  ││
│  └────────────────────────┘ └──────────────────────────┘│
│                                                         │
│  ┌──────────────────────────────────────────────────────┤
│  │  Recent Activity                                     │
│  │  ┌──────────────────────────────────────────────────┐│
│  │  │ 10:30 - New claim INC-2026-000150 declared       ││
│  │  │ 10:25 - Expert assigned to INC-2026-000148       ││
│  │  │ 10:20 - Status changed: INC-2026-000145          ││
│  │  │ ...                                               ││
│  │  └──────────────────────────────────────────────────┘│
│  └──────────────────────────────────────────────────────┤
└─────────────────────────────────────────────────────────┘
```

### KPI Cards Component

```typescript
interface KPIs {
  totalClaims: number;
  pendingCount: number;
  inProgressCount: number;
  closedThisMonth: number;
}
```

**Display**:
- Large number with label
- Color coding (pending: yellow, in-progress: blue)
- Click navigates to filtered claims list

### Status Chart Component

**Chart Type**: Pie or Donut chart
**Data**: statusDistribution object
**Colors**: Match status badge colors

| Status | Color |
|--------|-------|
| DECLARED | Gray |
| QUALIFIED | Blue |
| IN_PROGRESS | Yellow |
| RESOLVED | Green |
| ABANDONED | Red |

### Claims by Type Component

**Chart Type**: Horizontal bar chart
**Data**: claimsByType object
**Sort**: By count descending

### Recent Activity Component

**Display**: Timeline list
**Fields**:
- Time (relative or absolute)
- Event icon by type
- Description text
- Link to claim detail

---

## Frontend Implementation

### Angular Routes

```typescript
{
  path: 'dashboard',
  component: DashboardPageComponent
}
```

### Dashboard Service

```typescript
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private dashboard$ = signal<Dashboard | null>(null);
  private loading$ = signal<boolean>(false);

  loadDashboard(): Observable<Dashboard> {
    this.loading$.set(true);
    return this.api.getDashboard().pipe(
      tap(data => {
        this.dashboard$.set(data);
        this.loading$.set(false);
      })
    );
  }
}
```

### Dashboard Page Component

```typescript
@Component({
  selector: 'app-dashboard-page',
  template: `
    <div class="dashboard">
      <app-kpi-cards [kpis]="dashboard()?.kpis" />
      <div class="charts-row">
        <app-status-chart [data]="dashboard()?.statusDistribution" />
        <app-type-chart [data]="dashboard()?.claimsByType" />
      </div>
      <app-recent-activity [events]="dashboard()?.recentActivity" />
    </div>
  `
})
export class DashboardPageComponent {
  dashboard = inject(DashboardService).dashboard$;
}
```

---

## Data Models

### Dashboard DTO

```typescript
interface Dashboard {
  kpis: KPIs;
  statusDistribution: Record<string, number>;
  claimsByType: Record<string, number>;
  recentActivity: RecentActivity[];
}

interface KPIs {
  totalClaims: number;
  pendingCount: number;
  inProgressCount: number;
  closedThisMonth: number;
}

interface RecentActivity {
  eventType: string;
  description: string;
  claimReference: string;
  claimId: string;
  occurredAt: string;
}
```

---

## Caching and Performance

### Portal BFF Caching

- Dashboard data cached for 30 seconds
- Cache invalidated on claim updates
- Circuit breaker for incident service calls

### Frontend Caching

- Dashboard loaded on route activation
- Manual refresh button available
- Auto-refresh every 60 seconds (configurable)

---

## Testing

### Load Dashboard

```bash
curl http://localhost:8090/api/portal/v1/dashboard | jq
```

### Verify KPIs

```bash
# Total should match incident count
curl http://localhost:8085/api/v1/incidents | jq 'length'
```

### Frontend Access

Navigate to: `http://localhost:4200/dashboard`

---

## Related Documentation

- [PRD.md](../PRD.md) - Product requirements (FR-POR-001)
- [claims-management-ui.md](claims-management-ui.md) - Claims list and detail pages
- [incident-lifecycle.md](incident-lifecycle.md) - Incident status definitions
- [ARCHITECTURE.md](../ARCHITECTURE.md) - Portal BFF architecture
