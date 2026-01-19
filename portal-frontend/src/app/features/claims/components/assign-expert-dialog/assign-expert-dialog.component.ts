import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { ClaimsService } from '../../services/claims.service';
import { Actor } from '../../../../core/models/claim.model';

export interface AssignExpertDialogData {
  claimId: string;
}

@Component({
  selector: 'app-assign-expert-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule
  ],
  template: `
    <h2 mat-dialog-title>Assign Expert</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Expert</mat-label>
        <mat-select [(ngModel)]="expertId" required>
          @for (expert of experts; track expert.id) {
            <mat-option [value]="expert.id">{{ expert.name }} ({{ expert.email }})</mat-option>
          }
        </mat-select>
      </mat-form-field>

      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Scheduled Date</mat-label>
        <input matInput [matDatepicker]="picker" [(ngModel)]="scheduledDate">
        <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
        <mat-datepicker #picker></mat-datepicker>
      </mat-form-field>

      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Notes</mat-label>
        <textarea matInput [(ngModel)]="notes" rows="3" placeholder="Enter notes for the expert..."></textarea>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="onCancel()">Cancel</button>
      <button mat-raised-button color="primary" (click)="onAssign()" [disabled]="!expertId">
        Assign Expert
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width {
      width: 100%;
      margin-bottom: 8px;
    }

    mat-dialog-content {
      min-width: 300px;
    }
  `]
})
export class AssignExpertDialogComponent implements OnInit {
  experts: Actor[] = [];
  expertId = '';
  scheduledDate: Date | null = null;
  notes = '';

  constructor(
    public dialogRef: MatDialogRef<AssignExpertDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: AssignExpertDialogData,
    private claimsService: ClaimsService
  ) {}

  ngOnInit(): void {
    this.claimsService.getExperts().subscribe({
      next: (experts) => {
        this.experts = experts;
      }
    });
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onAssign(): void {
    this.dialogRef.close({
      expertId: this.expertId,
      scheduledDate: this.scheduledDate?.toISOString(),
      notes: this.notes || undefined
    });
  }
}
