import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Dashboard } from '../../../core/models/dashboard.model';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private readonly apiUrl = environment.apiUrl;

  private dashboardSignal = signal<Dashboard | null>(null);
  private loadingSignal = signal<boolean>(false);

  readonly dashboard = this.dashboardSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();

  constructor(private http: HttpClient) {}

  loadDashboard(): Observable<Dashboard> {
    this.loadingSignal.set(true);
    return this.http.get<Dashboard>(`${this.apiUrl}/dashboard`).pipe(
      tap({
        next: (dashboard) => {
          this.dashboardSignal.set(dashboard);
          this.loadingSignal.set(false);
        },
        error: () => {
          this.loadingSignal.set(false);
        }
      })
    );
  }
}
