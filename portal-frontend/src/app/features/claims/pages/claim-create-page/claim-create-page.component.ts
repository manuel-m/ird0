import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { ClaimsService } from '../../services/claims.service';
import { Actor, ClaimType, CLAIM_TYPE_LABELS, CreateClaimRequest } from '../../../../core/models/claim.model';

@Component({
  selector: 'app-claim-create-page',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    MatAutocompleteModule
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div class="header-left">
          <button mat-icon-button routerLink="/claims">
            <mat-icon>arrow_back</mat-icon>
          </button>
          <h1>Create New Claim</h1>
        </div>
      </div>

      <mat-card>
        <mat-card-content>
          <form [formGroup]="claimForm" (ngSubmit)="onSubmit()">
            <div class="form-grid">
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Policyholder</mat-label>
                <mat-select formControlName="policyholderId" required>
                  @for (ph of policyholders; track ph.id) {
                    <mat-option [value]="ph.id">{{ ph.name }} ({{ ph.email }})</mat-option>
                  }
                </mat-select>
                @if (claimForm.get('policyholderId')?.hasError('required')) {
                  <mat-error>Policyholder is required</mat-error>
                }
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Insurer</mat-label>
                <mat-select formControlName="insurerId" required>
                  @for (ins of insurers; track ins.id) {
                    <mat-option [value]="ins.id">{{ ins.name }}</mat-option>
                  }
                </mat-select>
                @if (claimForm.get('insurerId')?.hasError('required')) {
                  <mat-error>Insurer is required</mat-error>
                }
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Claim Type</mat-label>
                <mat-select formControlName="type" required>
                  @for (type of claimTypes; track type) {
                    <mat-option [value]="type">{{ getTypeLabel(type) }}</mat-option>
                  }
                </mat-select>
                @if (claimForm.get('type')?.hasError('required')) {
                  <mat-error>Type is required</mat-error>
                }
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Incident Date</mat-label>
                <input matInput [matDatepicker]="picker" formControlName="incidentDate" required>
                <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
                <mat-datepicker #picker></mat-datepicker>
                @if (claimForm.get('incidentDate')?.hasError('required')) {
                  <mat-error>Incident date is required</mat-error>
                }
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Estimated Damage</mat-label>
                <input matInput type="number" formControlName="estimatedDamage" placeholder="0.00">
                <span matTextSuffix>EUR</span>
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Location Address</mat-label>
                <input matInput formControlName="locationAddress" placeholder="Enter incident location">
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Description</mat-label>
                <textarea matInput formControlName="description" rows="4" placeholder="Describe the incident..."></textarea>
              </mat-form-field>
            </div>

            <div class="form-actions">
              <button mat-button type="button" routerLink="/claims">Cancel</button>
              <button mat-raised-button color="primary" type="submit" [disabled]="claimForm.invalid || isSubmitting">
                @if (isSubmitting) {
                  <mat-spinner diameter="20"></mat-spinner>
                } @else {
                  Create Claim
                }
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .header-left {
      display: flex;
      align-items: center;
      gap: 8px;

      h1 {
        margin: 0;
      }
    }

    .form-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 16px;

      .full-width {
        grid-column: span 2;
      }
    }

    @media (max-width: 768px) {
      .form-grid {
        grid-template-columns: 1fr;

        .full-width {
          grid-column: span 1;
        }
      }
    }

    .form-actions {
      display: flex;
      justify-content: flex-end;
      gap: 16px;
      margin-top: 24px;
      padding-top: 24px;
      border-top: 1px solid #eee;
    }

    mat-spinner {
      display: inline-block;
    }
  `]
})
export class ClaimCreatePageComponent implements OnInit {
  claimForm: FormGroup;
  isSubmitting = false;
  policyholders: Actor[] = [];
  insurers: Actor[] = [];

  claimTypes: ClaimType[] = [
    'WATER_DAMAGE',
    'FIRE',
    'THEFT',
    'LIABILITY',
    'PROPERTY_DAMAGE',
    'NATURAL_DISASTER',
    'OTHER'
  ];

  constructor(
    private fb: FormBuilder,
    private claimsService: ClaimsService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {
    this.claimForm = this.fb.group({
      policyholderId: ['', Validators.required],
      insurerId: ['', Validators.required],
      type: ['', Validators.required],
      incidentDate: [null, Validators.required],
      description: [''],
      estimatedDamage: [null],
      locationAddress: ['']
    });
  }

  ngOnInit(): void {
    this.loadActors();
  }

  loadActors(): void {
    this.claimsService.getPolicyholders().subscribe({
      next: (policyholders) => {
        this.policyholders = policyholders;
      }
    });

    this.claimsService.getInsurers().subscribe({
      next: (insurers) => {
        this.insurers = insurers;
      }
    });
  }

  onSubmit(): void {
    if (this.claimForm.invalid) {
      return;
    }

    this.isSubmitting = true;

    const formValue = this.claimForm.value;
    const request: CreateClaimRequest = {
      policyholderId: formValue.policyholderId,
      insurerId: formValue.insurerId,
      type: formValue.type,
      description: formValue.description || undefined,
      incidentDate: formValue.incidentDate.toISOString(),
      estimatedDamage: formValue.estimatedDamage || undefined,
      currency: 'EUR'
    };

    if (formValue.locationAddress) {
      request.location = {
        address: formValue.locationAddress
      };
    }

    this.claimsService.createClaim(request).subscribe({
      next: (claim) => {
        this.isSubmitting = false;
        this.snackBar.open('Claim created successfully', 'Close', { duration: 3000 });
        this.router.navigate(['/claims', claim.id]);
      },
      error: (err) => {
        this.isSubmitting = false;
        this.snackBar.open(err.message, 'Close', { duration: 5000 });
      }
    });
  }

  getTypeLabel(type: ClaimType): string {
    return CLAIM_TYPE_LABELS[type] || type;
  }
}
