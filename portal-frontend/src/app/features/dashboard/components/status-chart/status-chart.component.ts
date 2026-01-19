import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { CLAIM_STATUS_COLORS, CLAIM_STATUS_LABELS, CLAIM_TYPE_LABELS } from '../../../../core/models/claim.model';

@Component({
  selector: 'app-status-chart',
  standalone: true,
  imports: [CommonModule, MatCardModule],
  template: `
    <div class="charts-row">
      <mat-card class="chart-card">
        <mat-card-header>
          <mat-card-title>Claims by Status</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="status-bars">
            @for (item of statusItems; track item.status) {
              <div class="status-bar-item">
                <div class="status-label">
                  <span class="status-dot" [style.backgroundColor]="item.color"></span>
                  {{ item.label }}
                </div>
                <div class="status-bar-container">
                  <div
                    class="status-bar"
                    [style.width.%]="item.percentage"
                    [style.backgroundColor]="item.color">
                  </div>
                </div>
                <div class="status-count">{{ item.count }}</div>
              </div>
            }
          </div>
        </mat-card-content>
      </mat-card>

      <mat-card class="chart-card">
        <mat-card-header>
          <mat-card-title>Claims by Type</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div class="type-bars">
            @for (item of typeItems; track item.type) {
              <div class="type-bar-item">
                <div class="type-label">{{ item.label }}</div>
                <div class="type-bar-container">
                  <div
                    class="type-bar"
                    [style.width.%]="item.percentage">
                  </div>
                </div>
                <div class="type-count">{{ item.count }}</div>
              </div>
            }
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .charts-row {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 24px;
    }

    @media (max-width: 900px) {
      .charts-row {
        grid-template-columns: 1fr;
      }
    }

    .chart-card {
      mat-card-content {
        padding: 16px;
      }
    }

    .status-bars, .type-bars {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .status-bar-item, .type-bar-item {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .status-label, .type-label {
      width: 120px;
      font-size: 14px;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .status-dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;
    }

    .status-bar-container, .type-bar-container {
      flex: 1;
      height: 24px;
      background-color: #f5f5f5;
      border-radius: 4px;
      overflow: hidden;
    }

    .status-bar, .type-bar {
      height: 100%;
      border-radius: 4px;
      transition: width 0.3s ease;
    }

    .type-bar {
      background: linear-gradient(90deg, #1976d2, #42a5f5);
    }

    .status-count, .type-count {
      width: 40px;
      text-align: right;
      font-weight: 500;
    }
  `]
})
export class StatusChartComponent implements OnChanges {
  @Input() statusDistribution: Record<string, number> = {};
  @Input() claimsByType: Record<string, number> = {};

  statusItems: { status: string; label: string; count: number; percentage: number; color: string }[] = [];
  typeItems: { type: string; label: string; count: number; percentage: number }[] = [];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['statusDistribution']) {
      this.updateStatusItems();
    }
    if (changes['claimsByType']) {
      this.updateTypeItems();
    }
  }

  private updateStatusItems(): void {
    const total = Object.values(this.statusDistribution).reduce((sum, count) => sum + count, 0);
    this.statusItems = Object.entries(this.statusDistribution).map(([status, count]) => ({
      status,
      label: CLAIM_STATUS_LABELS[status as keyof typeof CLAIM_STATUS_LABELS] || status,
      count,
      percentage: total > 0 ? (count / total) * 100 : 0,
      color: CLAIM_STATUS_COLORS[status as keyof typeof CLAIM_STATUS_COLORS] || '#9e9e9e'
    }));
  }

  private updateTypeItems(): void {
    const total = Object.values(this.claimsByType).reduce((sum, count) => sum + count, 0);
    this.typeItems = Object.entries(this.claimsByType).map(([type, count]) => ({
      type,
      label: CLAIM_TYPE_LABELS[type as keyof typeof CLAIM_TYPE_LABELS] || type,
      count,
      percentage: total > 0 ? (count / total) * 100 : 0
    }));
  }
}
