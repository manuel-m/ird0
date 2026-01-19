export interface Dashboard {
  kpis: KPIs;
  statusDistribution: Record<string, number>;
  claimsByType: Record<string, number>;
  recentActivity: RecentActivity[];
}

export interface KPIs {
  totalClaims: number;
  pendingCount: number;
  inProgressCount: number;
  closedThisMonth: number;
}

export interface RecentActivity {
  eventType: string;
  description: string;
  claimReference: string;
  occurredAt: string;
}
