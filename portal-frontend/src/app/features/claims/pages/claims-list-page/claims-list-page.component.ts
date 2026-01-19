import { Component, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FormsModule } from '@angular/forms';
import { ClaimsService, ClaimsFilter } from '../../services/claims.service';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge.component';
import {
  ClaimSummary,
  ClaimStatus,
  ClaimType,
  CLAIM_STATUS_LABELS,
  CLAIM_TYPE_LABELS
} from '../../../../core/models/claim.model';

@Component({
  selector: 'app-claims-list-page',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatProgressSpinnerModule,
    FormsModule,
    StatusBadgeComponent,
    DatePipe
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <h1>Claims</h1>
        <button mat-raised-button color="primary" routerLink="/claims/new">
          <mat-icon>add</mat-icon>
          New Claim
        </button>
      </div>

      <!-- Filters -->
      <mat-card class="filters-card">
        <mat-card-content>
          <div class="filters-row">
            <mat-form-field appearance="outline">
              <mat-label>Status</mat-label>
              <mat-select [(ngModel)]="filter.status" (selectionChange)="applyFilters()">
                <mat-option [value]="undefined">All</mat-option>
                @for (status of statuses; track status) {
                  <mat-option [value]="status">{{ getStatusLabel(status) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Type</mat-label>
              <mat-select [(ngModel)]="filter.type" (selectionChange)="applyFilters()">
                <mat-option [value]="undefined">All</mat-option>
                @for (type of types; track type) {
                  <mat-option [value]="type">{{ getTypeLabel(type) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <button mat-button (click)="clearFilters()">
              <mat-icon>clear</mat-icon>
              Clear Filters
            </button>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Table -->
      <mat-card>
        @if (claimsService.loading()) {
          <div class="loading-container">
            <mat-spinner diameter="40"></mat-spinner>
          </div>
        } @else {
          <table mat-table [dataSource]="claimsService.claims()" matSort (matSortChange)="sortData($event)">
            <ng-container matColumnDef="referenceNumber">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Reference</th>
              <td mat-cell *matCellDef="let claim">
                <a [routerLink]="['/claims', claim.id]" class="claim-link">
                  {{ claim.referenceNumber }}
                </a>
              </td>
            </ng-container>

            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Status</th>
              <td mat-cell *matCellDef="let claim">
                <app-status-badge [status]="claim.status"></app-status-badge>
              </td>
            </ng-container>

            <ng-container matColumnDef="type">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Type</th>
              <td mat-cell *matCellDef="let claim">{{ getTypeLabel(claim.type) }}</td>
            </ng-container>

            <ng-container matColumnDef="policyholderName">
              <th mat-header-cell *matHeaderCellDef>Policyholder</th>
              <td mat-cell *matCellDef="let claim">{{ claim.policyholderName }}</td>
            </ng-container>

            <ng-container matColumnDef="insurerName">
              <th mat-header-cell *matHeaderCellDef>Insurer</th>
              <td mat-cell *matCellDef="let claim">{{ claim.insurerName }}</td>
            </ng-container>

            <ng-container matColumnDef="estimatedDamage">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Damage</th>
              <td mat-cell *matCellDef="let claim">
                @if (claim.estimatedDamage) {
                  {{ claim.estimatedDamage | number:'1.2-2' }} {{ claim.currency || 'EUR' }}
                }
              </td>
            </ng-container>

            <ng-container matColumnDef="incidentDate">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Incident Date</th>
              <td mat-cell *matCellDef="let claim">{{ claim.incidentDate | date:'mediumDate' }}</td>
            </ng-container>

            <ng-container matColumnDef="createdAt">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Created</th>
              <td mat-cell *matCellDef="let claim">{{ claim.createdAt | date:'mediumDate' }}</td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns;"
                [routerLink]="['/claims', row.id]"
                class="clickable-row">
            </tr>
          </table>

          <mat-paginator
            [length]="claimsService.totalElements()"
            [pageSize]="claimsService.pageSize()"
            [pageIndex]="claimsService.pageIndex()"
            [pageSizeOptions]="[10, 20, 50]"
            (page)="onPageChange($event)"
            showFirstLastButtons>
          </mat-paginator>
        }
      </mat-card>
    </div>
  `,
  styles: [`
    .filters-card {
      margin-bottom: 24px;
    }

    .filters-row {
      display: flex;
      gap: 16px;
      align-items: center;
      flex-wrap: wrap;
    }

    mat-form-field {
      min-width: 150px;
    }

    .loading-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 200px;
    }

    table {
      width: 100%;
    }

    .claim-link {
      color: #1976d2;
      text-decoration: none;
      font-family: monospace;
      font-weight: 500;

      &:hover {
        text-decoration: underline;
      }
    }

    .clickable-row {
      cursor: pointer;

      &:hover {
        background-color: #f5f5f5;
      }
    }

    mat-paginator {
      border-top: 1px solid #e0e0e0;
    }
  `]
})
export class ClaimsListPageComponent implements OnInit {
  displayedColumns = [
    'referenceNumber',
    'status',
    'type',
    'policyholderName',
    'insurerName',
    'estimatedDamage',
    'incidentDate',
    'createdAt'
  ];

  filter: ClaimsFilter = {};
  currentSort = 'createdAt,desc';

  statuses: ClaimStatus[] = [
    'DECLARED',
    'UNDER_REVIEW',
    'QUALIFIED',
    'IN_PROGRESS',
    'CLOSED',
    'ABANDONED'
  ];

  types: ClaimType[] = [
    'WATER_DAMAGE',
    'FIRE',
    'THEFT',
    'LIABILITY',
    'PROPERTY_DAMAGE',
    'NATURAL_DISASTER',
    'OTHER'
  ];

  constructor(
    public claimsService: ClaimsService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe((params) => {
      if (params['status']) {
        this.filter.status = params['status'];
      }
      if (params['type']) {
        this.filter.type = params['type'];
      }
      this.loadClaims();
    });
  }

  loadClaims(): void {
    this.claimsService
      .loadClaims(this.filter, this.claimsService.pageIndex(), this.claimsService.pageSize(), this.currentSort)
      .subscribe();
  }

  applyFilters(): void {
    this.router.navigate([], {
      queryParams: {
        status: this.filter.status || null,
        type: this.filter.type || null
      },
      queryParamsHandling: 'merge'
    });
    this.loadClaims();
  }

  clearFilters(): void {
    this.filter = {};
    this.router.navigate([], { queryParams: {} });
    this.loadClaims();
  }

  sortData(sort: Sort): void {
    if (sort.active && sort.direction) {
      this.currentSort = `${sort.active},${sort.direction}`;
    } else {
      this.currentSort = 'createdAt,desc';
    }
    this.loadClaims();
  }

  onPageChange(event: PageEvent): void {
    this.claimsService.loadClaims(this.filter, event.pageIndex, event.pageSize, this.currentSort).subscribe();
  }

  getStatusLabel(status: ClaimStatus): string {
    return CLAIM_STATUS_LABELS[status] || status;
  }

  getTypeLabel(type: ClaimType | string): string {
    return CLAIM_TYPE_LABELS[type as ClaimType] || type;
  }
}
