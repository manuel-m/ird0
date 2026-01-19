import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { CLAIM_STATUS_LABELS } from '../../../../core/models/claim.model';

export interface StatusUpdateDialogData {
  currentStatus: string;
  targetStatus: string;
}

@Component({
  selector: 'app-status-update-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule
  ],
  template: `
    <h2 mat-dialog-title>Update Status</h2>
    <mat-dialog-content>
      <p>
        Change status from <strong>{{ getStatusLabel(data.currentStatus) }}</strong>
        to <strong>{{ getStatusLabel(data.targetStatus) }}</strong>?
      </p>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Reason (optional)</mat-label>
        <textarea matInput [(ngModel)]="reason" rows="3" placeholder="Enter a reason for this status change..."></textarea>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="onCancel()">Cancel</button>
      <button mat-raised-button [color]="getButtonColor()" (click)="onConfirm()">
        {{ getStatusLabel(data.targetStatus) }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width {
      width: 100%;
    }

    mat-dialog-content {
      min-width: 300px;
    }

    p {
      margin-bottom: 16px;
    }
  `]
})
export class StatusUpdateDialogComponent {
  reason = '';

  constructor(
    public dialogRef: MatDialogRef<StatusUpdateDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: StatusUpdateDialogData
  ) {}

  onCancel(): void {
    this.dialogRef.close();
  }

  onConfirm(): void {
    this.dialogRef.close({
      status: this.data.targetStatus,
      reason: this.reason || undefined
    });
  }

  getStatusLabel(status: string): string {
    return CLAIM_STATUS_LABELS[status as keyof typeof CLAIM_STATUS_LABELS] || status;
  }

  getButtonColor(): string {
    if (this.data.targetStatus === 'ABANDONED') return 'warn';
    if (this.data.targetStatus === 'CLOSED') return 'primary';
    return 'accent';
  }
}
