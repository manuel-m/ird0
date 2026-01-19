import { Injectable, signal, computed } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../../environments/environment';
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
  private readonly apiUrl = environment.apiUrl;

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

  constructor(private http: HttpClient) {}

  loadClaims(
    filter: ClaimsFilter = {},
    page: number = 0,
    size: number = 20,
    sort: string = 'createdAt,desc'
  ): Observable<Page<ClaimSummary>> {
    this.loadingSignal.set(true);

    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', sort);

    if (filter.policyholderId) {
      params = params.set('policyholderId', filter.policyholderId);
    }
    if (filter.insurerId) {
      params = params.set('insurerId', filter.insurerId);
    }
    if (filter.status) {
      params = params.set('status', filter.status);
    }
    if (filter.type) {
      params = params.set('type', filter.type);
    }
    if (filter.fromDate) {
      params = params.set('fromDate', filter.fromDate);
    }
    if (filter.toDate) {
      params = params.set('toDate', filter.toDate);
    }

    return this.http.get<Page<ClaimSummary>>(`${this.apiUrl}/claims`, { params }).pipe(
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
    return this.http.get<ClaimDetail>(`${this.apiUrl}/claims/${id}`).pipe(
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
    return this.http.post<ClaimDetail>(`${this.apiUrl}/claims`, request).pipe(
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
    return this.http.put<ClaimDetail>(`${this.apiUrl}/claims/${id}/status`, request).pipe(
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
    return this.http.post<ClaimDetail>(`${this.apiUrl}/claims/${id}/expert`, request).pipe(
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
    return this.http.post<ClaimDetail>(`${this.apiUrl}/claims/${id}/comments`, request).pipe(
      tap((claim) => {
        this.selectedClaimSignal.set(claim);
      })
    );
  }

  getComments(id: string): Observable<Comment[]> {
    return this.http.get<Comment[]>(`${this.apiUrl}/claims/${id}/comments`);
  }

  getHistory(id: string): Observable<Event[]> {
    return this.http.get<Event[]>(`${this.apiUrl}/claims/${id}/history`);
  }

  getExperts(): Observable<Actor[]> {
    return this.http.get<Actor[]>(`${this.apiUrl}/experts`);
  }

  getPolicyholders(): Observable<Actor[]> {
    return this.http.get<Actor[]>(`${this.apiUrl}/policyholders`);
  }

  getInsurers(): Observable<Actor[]> {
    return this.http.get<Actor[]>(`${this.apiUrl}/insurers`);
  }

  clearSelectedClaim(): void {
    this.selectedClaimSignal.set(null);
  }
}
