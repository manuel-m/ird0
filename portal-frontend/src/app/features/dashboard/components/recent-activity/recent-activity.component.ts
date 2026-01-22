import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { RecentActivity } from '../../../../core/models/dashboard.model';
import { DateAgoPipe } from '../../../../shared/pipes/date-ago.pipe';

@Component({
  selector: 'app-recent-activity',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatListModule, DateAgoPipe],
  template: `
    <mat-card>
      <mat-card-header>
        <mat-card-title>Recent Activity</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        @if (activities.length === 0) {
          <p class="no-activity">No recent activity</p>
        } @else {
          <mat-list>
            @for (activity of activities; track activity.claimReference) {
              <mat-list-item>
                <mat-icon matListItemIcon [class]="getIconClass(activity.eventType)">
                  {{ getIcon(activity.eventType) }}
                </mat-icon>
                <div matListItemTitle>{{ activity.description }}</div>
                <div matListItemLine class="activity-meta">
                  <span class="claim-ref">{{ activity.claimReference }}</span>
                  <span class="time-ago">{{ activity.occurredAt | dateAgo }}</span>
                </div>
              </mat-list-item>
            }
          </mat-list>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .no-activity {
      text-align: center;
      color: #666;
      padding: 24px;
    }

    .activity-meta {
      display: flex;
      gap: 16px;
      font-size: 12px;
      color: #999;
    }

    .claim-ref {
      font-family: monospace;
    }

    mat-icon {
      &.declared { color: #1976d2; }
      &.under-review { color: #ffb300; }
      &.qualified { color: #ff9800; }
      &.in-progress { color: #9c27b0; }
      &.closed { color: #4caf50; }
      &.abandoned { color: #9e9e9e; }
    }
  `]
})
export class RecentActivityComponent {
  @Input() activities: RecentActivity[] = [];

  getIcon(eventType: string): string {
    const iconMap: Record<string, string> = {
      CLAIM_DECLARED: 'add_circle',
      CLAIM_UNDER_REVIEW: 'pending',
      CLAIM_QUALIFIED: 'verified',
      CLAIM_IN_PROGRESS: 'engineering',
      CLAIM_CLOSED: 'check_circle',
      CLAIM_ABANDONED: 'cancel'
    };
    return iconMap[eventType] || 'info';
  }

  getIconClass(eventType: string): string {
    const classMap: Record<string, string> = {
      CLAIM_DECLARED: 'declared',
      CLAIM_UNDER_REVIEW: 'under-review',
      CLAIM_QUALIFIED: 'qualified',
      CLAIM_IN_PROGRESS: 'in-progress',
      CLAIM_CLOSED: 'closed',
      CLAIM_ABANDONED: 'abandoned'
    };
    return classMap[eventType] || '';
  }
}
