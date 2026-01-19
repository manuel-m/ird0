import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { KPIs } from '../../../../core/models/dashboard.model';

@Component({
  selector: 'app-kpi-cards',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, RouterLink],
  template: `
    <div class="kpi-grid">
      <mat-card class="kpi-card" routerLink="/claims">
        <mat-card-content>
          <div class="kpi-icon total">
            <mat-icon>description</mat-icon>
          </div>
          <div class="kpi-info">
            <div class="kpi-value">{{ kpis.totalClaims }}</div>
            <div class="kpi-label">Total Claims</div>
          </div>
        </mat-card-content>
      </mat-card>

      <mat-card class="kpi-card" [routerLink]="['/claims']" [queryParams]="{status: 'DECLARED'}">
        <mat-card-content>
          <div class="kpi-icon pending">
            <mat-icon>pending_actions</mat-icon>
          </div>
          <div class="kpi-info">
            <div class="kpi-value">{{ kpis.pendingCount }}</div>
            <div class="kpi-label">Pending</div>
          </div>
        </mat-card-content>
      </mat-card>

      <mat-card class="kpi-card" [routerLink]="['/claims']" [queryParams]="{status: 'IN_PROGRESS'}">
        <mat-card-content>
          <div class="kpi-icon in-progress">
            <mat-icon>engineering</mat-icon>
          </div>
          <div class="kpi-info">
            <div class="kpi-value">{{ kpis.inProgressCount }}</div>
            <div class="kpi-label">In Progress</div>
          </div>
        </mat-card-content>
      </mat-card>

      <mat-card class="kpi-card" [routerLink]="['/claims']" [queryParams]="{status: 'CLOSED'}">
        <mat-card-content>
          <div class="kpi-icon closed">
            <mat-icon>check_circle</mat-icon>
          </div>
          <div class="kpi-info">
            <div class="kpi-value">{{ kpis.closedThisMonth }}</div>
            <div class="kpi-label">Closed This Month</div>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 24px;
      margin-bottom: 24px;
    }

    @media (max-width: 1200px) {
      .kpi-grid {
        grid-template-columns: repeat(2, 1fr);
      }
    }

    @media (max-width: 600px) {
      .kpi-grid {
        grid-template-columns: 1fr;
      }
    }

    .kpi-card {
      cursor: pointer;
      transition: transform 0.2s, box-shadow 0.2s;

      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
      }

      mat-card-content {
        display: flex;
        align-items: center;
        gap: 16px;
        padding: 16px !important;
      }
    }

    .kpi-icon {
      width: 56px;
      height: 56px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;

      mat-icon {
        font-size: 28px;
        width: 28px;
        height: 28px;
        color: white;
      }

      &.total {
        background: linear-gradient(135deg, #1976d2, #42a5f5);
      }

      &.pending {
        background: linear-gradient(135deg, #ffb300, #ffca28);
      }

      &.in-progress {
        background: linear-gradient(135deg, #9c27b0, #ba68c8);
      }

      &.closed {
        background: linear-gradient(135deg, #4caf50, #81c784);
      }
    }

    .kpi-info {
      flex: 1;
    }

    .kpi-value {
      font-size: 28px;
      font-weight: 600;
      color: #333;
    }

    .kpi-label {
      font-size: 14px;
      color: #666;
    }
  `]
})
export class KpiCardsComponent {
  @Input({ required: true }) kpis!: KPIs;
}
