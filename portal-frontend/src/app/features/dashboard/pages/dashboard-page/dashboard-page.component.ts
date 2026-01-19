import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { DashboardService } from '../../services/dashboard.service';
import { KpiCardsComponent } from '../../components/kpi-cards/kpi-cards.component';
import { StatusChartComponent } from '../../components/status-chart/status-chart.component';
import { RecentActivityComponent } from '../../components/recent-activity/recent-activity.component';

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    RouterLink,
    KpiCardsComponent,
    StatusChartComponent,
    RecentActivityComponent
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <h1>Dashboard</h1>
      </div>

      @if (dashboardService.loading()) {
        <div class="loading-container">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      } @else if (dashboardService.dashboard()) {
        <app-kpi-cards [kpis]="dashboardService.dashboard()!.kpis"></app-kpi-cards>

        <div class="charts-grid">
          <app-status-chart
            [statusDistribution]="dashboardService.dashboard()!.statusDistribution"
            [claimsByType]="dashboardService.dashboard()!.claimsByType">
          </app-status-chart>
        </div>

        <app-recent-activity
          [activities]="dashboardService.dashboard()!.recentActivity">
        </app-recent-activity>
      } @else {
        <mat-card>
          <mat-card-content>
            <p>No data available</p>
          </mat-card-content>
        </mat-card>
      }
    </div>
  `,
  styles: [`
    .loading-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 200px;
    }

    .charts-grid {
      margin-bottom: 24px;
    }
  `]
})
export class DashboardPageComponent implements OnInit {
  constructor(public dashboardService: DashboardService) {}

  ngOnInit(): void {
    this.dashboardService.loadDashboard().subscribe();
  }
}
