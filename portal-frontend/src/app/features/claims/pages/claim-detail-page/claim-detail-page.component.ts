import { Component, OnInit, OnDestroy, Input } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { ClaimsService } from '../../services/claims.service';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge.component';
import { ActorCardComponent } from '../../../../shared/components/actor-card/actor-card.component';
import { DateAgoPipe } from '../../../../shared/pipes/date-ago.pipe';
import { ClaimDetail, CLAIM_TYPE_LABELS, CLAIM_STATUS_LABELS } from '../../../../core/models/claim.model';
import { StatusUpdateDialogComponent } from '../../components/status-update-dialog/status-update-dialog.component';
import { AssignExpertDialogComponent } from '../../components/assign-expert-dialog/assign-expert-dialog.component';

@Component({
  selector: 'app-claim-detail-page',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTabsModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDialogModule,
    StatusBadgeComponent,
    ActorCardComponent,
    DateAgoPipe,
    DatePipe
  ],
  template: `
    <div class="page-container">
      @if (claimsService.loading()) {
        <div class="loading-container">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      } @else if (claim) {
        <div class="page-header">
          <div class="header-left">
            <button mat-icon-button routerLink="/claims">
              <mat-icon>arrow_back</mat-icon>
            </button>
            <div>
              <h1>{{ claim.referenceNumber }}</h1>
              <div class="header-meta">
                <app-status-badge [status]="claim.status"></app-status-badge>
                <span class="claim-type">{{ getTypeLabel(claim.type) }}</span>
              </div>
            </div>
          </div>
          <div class="header-actions">
            @for (transition of claim.availableTransitions; track transition) {
              <button mat-raised-button [color]="getTransitionColor(transition)" (click)="openStatusDialog(transition)">
                {{ getStatusLabel(transition) }}
              </button>
            }
            @if (claim.status === 'QUALIFIED') {
              <button mat-raised-button color="accent" (click)="openAssignExpertDialog()">
                <mat-icon>person_add</mat-icon>
                Assign Expert
              </button>
            }
          </div>
        </div>

        <div class="content-grid">
          <!-- Main Content -->
          <div class="main-content">
            <mat-card>
              <mat-card-header>
                <mat-card-title>Incident Details</mat-card-title>
              </mat-card-header>
              <mat-card-content>
                <div class="detail-grid">
                  <div class="detail-item">
                    <span class="detail-label">Incident Date</span>
                    <span class="detail-value">{{ claim.incidentDate | date:'medium' }}</span>
                  </div>
                  @if (claim.estimatedDamage) {
                    <div class="detail-item">
                      <span class="detail-label">Estimated Damage</span>
                      <span class="detail-value">{{ claim.estimatedDamage | number:'1.2-2' }} {{ claim.currency || 'EUR' }}</span>
                    </div>
                  }
                  @if (claim.location?.address) {
                    <div class="detail-item full-width">
                      <span class="detail-label">Location</span>
                      <span class="detail-value">{{ claim.location?.address }}</span>
                    </div>
                  }
                  @if (claim.description) {
                    <div class="detail-item full-width">
                      <span class="detail-label">Description</span>
                      <span class="detail-value">{{ claim.description }}</span>
                    </div>
                  }
                </div>
              </mat-card-content>
            </mat-card>

            <mat-card>
              <mat-tab-group>
                <mat-tab label="Comments ({{ claim.comments.length }})">
                  <div class="tab-content">
                    @if (claim.comments.length) {
                      @for (comment of claim.comments; track comment.id) {
                        <div class="comment-item">
                          <div class="comment-header">
                            <span class="comment-author">{{ comment.authorName || comment.authorType }}</span>
                            <span class="comment-time">{{ comment.createdAt | dateAgo }}</span>
                          </div>
                          <div class="comment-content">{{ comment.content }}</div>
                        </div>
                      }
                    } @else {
                      <p class="no-data">No comments yet</p>
                    }
                  </div>
                </mat-tab>

                <mat-tab label="History">
                  <div class="tab-content">
                    @if (history.length) {
                      <div class="timeline">
                        @for (event of history; track event.id) {
                          <div class="timeline-item">
                            <div class="timeline-marker"></div>
                            <div class="timeline-content">
                              <div class="timeline-header">
                                <span class="event-type">{{ event.eventType }}</span>
                                <span class="event-time">{{ event.occurredAt | dateAgo }}</span>
                              </div>
                              @if (event.description) {
                                <div class="event-description">{{ event.description }}</div>
                              }
                            </div>
                          </div>
                        }
                      </div>
                    } @else {
                      <p class="no-data">No history available</p>
                    }
                  </div>
                </mat-tab>

                <mat-tab label="Experts ({{ claim.expertAssignments.length }})">
                  <div class="tab-content">
                    @if (claim.expertAssignments.length) {
                      @for (assignment of claim.expertAssignments; track assignment.id) {
                        <mat-card class="expert-card">
                          <mat-card-content>
                            <div class="expert-info">
                              <strong>{{ assignment.expert.name }}</strong>
                              @if (assignment.scheduledDate) {
                                <span>Scheduled: {{ assignment.scheduledDate | date:'medium' }}</span>
                              }
                              @if (assignment.notes) {
                                <span class="notes">{{ assignment.notes }}</span>
                              }
                            </div>
                          </mat-card-content>
                        </mat-card>
                      }
                    } @else {
                      <p class="no-data">No experts assigned</p>
                    }
                  </div>
                </mat-tab>
              </mat-tab-group>
            </mat-card>
          </div>

          <!-- Sidebar -->
          <div class="sidebar">
            <app-actor-card [actor]="claim.policyholder" icon="person"></app-actor-card>
            <app-actor-card [actor]="claim.insurer" icon="business"></app-actor-card>

            <mat-card>
              <mat-card-header>
                <mat-card-title>Timeline</mat-card-title>
              </mat-card-header>
              <mat-card-content>
                <div class="meta-item">
                  <span class="meta-label">Created</span>
                  <span class="meta-value">{{ claim.createdAt | date:'medium' }}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">Last Updated</span>
                  <span class="meta-value">{{ claim.updatedAt | date:'medium' }}</span>
                </div>
              </mat-card-content>
            </mat-card>
          </div>
        </div>
      } @else {
        <mat-card>
          <mat-card-content>
            <p>Claim not found</p>
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
      min-height: 400px;
    }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 24px;
    }

    .header-left {
      display: flex;
      align-items: flex-start;
      gap: 8px;

      h1 {
        margin: 0;
        font-family: monospace;
      }
    }

    .header-meta {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-top: 8px;
    }

    .claim-type {
      color: #666;
    }

    .header-actions {
      display: flex;
      gap: 8px;
    }

    .content-grid {
      display: grid;
      grid-template-columns: 1fr 350px;
      gap: 24px;
    }

    @media (max-width: 1024px) {
      .content-grid {
        grid-template-columns: 1fr;
      }
    }

    .main-content {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .detail-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 16px;

      .full-width {
        grid-column: span 2;
      }
    }

    .detail-item {
      .detail-label {
        display: block;
        font-size: 12px;
        color: #666;
        margin-bottom: 4px;
      }

      .detail-value {
        font-size: 14px;
        color: #333;
      }
    }

    .tab-content {
      padding: 16px;
      min-height: 200px;
    }

    .no-data {
      text-align: center;
      color: #666;
      padding: 24px;
    }

    .comment-item {
      padding: 12px;
      border-bottom: 1px solid #eee;

      &:last-child {
        border-bottom: none;
      }
    }

    .comment-header {
      display: flex;
      justify-content: space-between;
      margin-bottom: 8px;
    }

    .comment-author {
      font-weight: 500;
    }

    .comment-time {
      font-size: 12px;
      color: #999;
    }

    .comment-content {
      color: #333;
    }

    .timeline {
      position: relative;
      padding-left: 24px;
    }

    .timeline-item {
      position: relative;
      padding-bottom: 16px;

      &:last-child {
        padding-bottom: 0;
      }
    }

    .timeline-marker {
      position: absolute;
      left: -24px;
      width: 12px;
      height: 12px;
      background: #1976d2;
      border-radius: 50%;
      border: 2px solid white;
      box-shadow: 0 0 0 2px #1976d2;
    }

    .timeline-content {
      padding-left: 8px;
    }

    .timeline-header {
      display: flex;
      justify-content: space-between;
      margin-bottom: 4px;
    }

    .event-type {
      font-weight: 500;
      font-size: 14px;
    }

    .event-time {
      font-size: 12px;
      color: #999;
    }

    .event-description {
      font-size: 14px;
      color: #666;
    }

    .sidebar {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .meta-item {
      display: flex;
      justify-content: space-between;
      padding: 8px 0;
      border-bottom: 1px solid #eee;

      &:last-child {
        border-bottom: none;
      }
    }

    .meta-label {
      color: #666;
    }

    .meta-value {
      color: #333;
    }

    .expert-card {
      margin-bottom: 8px;
    }

    .expert-info {
      display: flex;
      flex-direction: column;
      gap: 4px;

      .notes {
        font-size: 12px;
        color: #666;
      }
    }
  `]
})
export class ClaimDetailPageComponent implements OnInit, OnDestroy {
  @Input() id!: string;

  history: any[] = [];

  constructor(
    public claimsService: ClaimsService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  get claim(): ClaimDetail | null {
    return this.claimsService.selectedClaim();
  }

  ngOnInit(): void {
    this.loadClaim();
  }

  ngOnDestroy(): void {
    this.claimsService.clearSelectedClaim();
  }

  loadClaim(): void {
    this.claimsService.getClaimById(this.id).subscribe({
      next: () => {
        this.loadHistory();
      },
      error: (err) => {
        this.snackBar.open(err.message, 'Close', { duration: 5000 });
      }
    });
  }

  loadHistory(): void {
    this.claimsService.getHistory(this.id).subscribe({
      next: (history) => {
        this.history = history;
      }
    });
  }

  openStatusDialog(targetStatus: string): void {
    const dialogRef = this.dialog.open(StatusUpdateDialogComponent, {
      width: '400px',
      data: { currentStatus: this.claim?.status, targetStatus }
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.claimsService.updateStatus(this.id, result).subscribe({
          next: () => {
            this.snackBar.open('Status updated successfully', 'Close', { duration: 3000 });
            this.loadHistory();
          },
          error: (err) => {
            this.snackBar.open(err.message, 'Close', { duration: 5000 });
          }
        });
      }
    });
  }

  openAssignExpertDialog(): void {
    const dialogRef = this.dialog.open(AssignExpertDialogComponent, {
      width: '400px',
      data: { claimId: this.id }
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.claimsService.assignExpert(this.id, result).subscribe({
          next: () => {
            this.snackBar.open('Expert assigned successfully', 'Close', { duration: 3000 });
            this.loadHistory();
          },
          error: (err) => {
            this.snackBar.open(err.message, 'Close', { duration: 5000 });
          }
        });
      }
    });
  }

  getTypeLabel(type: string): string {
    return CLAIM_TYPE_LABELS[type as keyof typeof CLAIM_TYPE_LABELS] || type;
  }

  getStatusLabel(status: string): string {
    return CLAIM_STATUS_LABELS[status as keyof typeof CLAIM_STATUS_LABELS] || status;
  }

  getTransitionColor(status: string): string {
    if (status === 'ABANDONED') return 'warn';
    if (status === 'CLOSED') return 'primary';
    return 'accent';
  }
}
