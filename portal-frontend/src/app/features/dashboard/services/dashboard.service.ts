import { Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { Dashboard } from '../../../core/models/dashboard.model';
import { DashboardService as GeneratedDashboardService } from '../../../generated/api';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private dashboardSignal = signal<Dashboard | null>(null);
  private loadingSignal = signal<boolean>(false);

  readonly dashboard = this.dashboardSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();

  constructor(private api: GeneratedDashboardService) {}

  loadDashboard(): Observable<Dashboard> {
    this.loadingSignal.set(true);
    return (this.api.getDashboard() as Observable<Dashboard>).pipe(
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
