import { Injectable, signal, computed } from '@angular/core';
import { Observable, tap } from 'rxjs';
import {
  ClaimSummary,
  ClaimDetail,
  CreateClaimRequest,
  StatusUpdateRequest,
  ExpertAssignmentRequest,
  CommentRequest,
  Comment,
  Event,
  Actor,
  Page
} from '../../../core/models/claim.model';
import { ClaimsService as GeneratedClaimsService } from '../../../generated/api';

export interface ClaimsFilter {
  policyholderId?: string;
  insurerId?: string;
  status?: string;
  type?: string;
  fromDate?: string;
  toDate?: string;
}

@Injectable({
  providedIn: 'root'
})
export class ClaimsService {
  // Signals for reactive state
  private claimsSignal = signal<ClaimSummary[]>([]);
  private selectedClaimSignal = signal<ClaimDetail | null>(null);
  private loadingSignal = signal<boolean>(false);
  private totalElementsSignal = signal<number>(0);
  private pageSizeSignal = signal<number>(20);
  private pageIndexSignal = signal<number>(0);

  // Public readonly signals
  readonly claims = this.claimsSignal.asReadonly();
  readonly selectedClaim = this.selectedClaimSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();
  readonly totalElements = this.totalElementsSignal.asReadonly();
  readonly pageSize = this.pageSizeSignal.asReadonly();
  readonly pageIndex = this.pageIndexSignal.asReadonly();

  // Computed derived state
  readonly pendingClaims = computed(() =>
    this.claimsSignal().filter((c) =>
      ['DECLARED', 'UNDER_REVIEW'].includes(c.status)
    )
  );

  readonly inProgressClaims = computed(() =>
    this.claimsSignal().filter((c) =>
      ['QUALIFIED', 'IN_PROGRESS'].includes(c.status)
    )
  );

  readonly closedClaims = computed(() =>
    this.claimsSignal().filter((c) => c.status === 'CLOSED')
  );

  constructor(private api: GeneratedClaimsService) {}

  loadClaims(
    filter: ClaimsFilter = {},
    page: number = 0,
    size: number = 20,
    sort: string = 'createdAt,desc'
  ): Observable<Page<ClaimSummary>> {
    this.loadingSignal.set(true);

    return (this.api.getClaims(
      filter.policyholderId,
      filter.insurerId,
      filter.status,
      filter.type,
      filter.fromDate,
      filter.toDate,
      page,
      size,
      sort
    ) as Observable<Page<ClaimSummary>>).pipe(
      tap({
        next: (response) => {
          this.claimsSignal.set(response.content);
          this.totalElementsSignal.set(response.totalElements);
          this.pageIndexSignal.set(response.number);
          this.pageSizeSignal.set(response.size);
          this.loadingSignal.set(false);
        },
        error: () => {
          this.loadingSignal.set(false);
        }
      })
    );
  }

  getClaimById(id: string): Observable<ClaimDetail> {
    this.loadingSignal.set(true);
    return (this.api.getClaimById(id) as Observable<ClaimDetail>).pipe(
      tap({
        next: (claim) => {
          this.selectedClaimSignal.set(claim);
          this.loadingSignal.set(false);
        },
        error: () => {
          this.loadingSignal.set(false);
        }
      })
    );
  }

  createClaim(request: CreateClaimRequest): Observable<ClaimDetail> {
    this.loadingSignal.set(true);
    return (this.api.createClaim(request) as Observable<ClaimDetail>).pipe(
      tap({
        next: (claim) => {
          this.selectedClaimSignal.set(claim);
          this.loadingSignal.set(false);
        },
        error: () => {
          this.loadingSignal.set(false);
        }
      })
    );
  }

  updateStatus(id: string, request: StatusUpdateRequest): Observable<ClaimDetail> {
    this.loadingSignal.set(true);
    return (this.api.updateClaimStatus(id, request) as Observable<ClaimDetail>).pipe(
      tap({
        next: (claim) => {
          this.selectedClaimSignal.set(claim);
          this.loadingSignal.set(false);
        },
        error: () => {
          this.loadingSignal.set(false);
        }
      })
    );
  }

  assignExpert(id: string, request: ExpertAssignmentRequest): Observable<ClaimDetail> {
    this.loadingSignal.set(true);
    return (this.api.assignExpert(id, request) as Observable<ClaimDetail>).pipe(
      tap({
        next: (claim) => {
          this.selectedClaimSignal.set(claim);
          this.loadingSignal.set(false);
        },
        error: () => {
          this.loadingSignal.set(false);
        }
      })
    );
  }

  addComment(id: string, request: CommentRequest): Observable<ClaimDetail> {
    return (this.api.addClaimComment(id, request) as Observable<ClaimDetail>).pipe(
      tap((claim) => {
        this.selectedClaimSignal.set(claim);
      })
    );
  }

  getComments(id: string): Observable<Comment[]> {
    return this.api.getClaimComments(id) as Observable<Comment[]>;
  }

  getHistory(id: string): Observable<Event[]> {
    return this.api.getClaimHistory(id) as Observable<Event[]>;
  }

  getExperts(): Observable<Actor[]> {
    return this.api.getExperts() as Observable<Actor[]>;
  }

  getPolicyholders(): Observable<Actor[]> {
    return this.api.getPolicyholders() as Observable<Actor[]>;
  }

  getInsurers(): Observable<Actor[]> {
    return this.api.getInsurers() as Observable<Actor[]>;
  }

  clearSelectedClaim(): void {
    this.selectedClaimSignal.set(null);
  }
}
