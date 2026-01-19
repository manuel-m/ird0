import { Injectable, signal, computed } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class LoadingService {
  private loadingSubject = new BehaviorSubject<boolean>(false);
  private loadingCount = signal(0);

  loading$ = this.loadingSubject.asObservable();
  readonly loading = computed(() => this.loadingCount() > 0);

  show(): void {
    this.loadingCount.update(count => count + 1);
    this.loadingSubject.next(true);
  }

  hide(): void {
    this.loadingCount.update(count => Math.max(0, count - 1));
    if (this.loadingCount() === 0) {
      this.loadingSubject.next(false);
    }
  }
}
