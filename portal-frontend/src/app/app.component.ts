import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { LoadingService } from './core/services/loading.service';
import { AsyncPipe } from '@angular/common';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatSidenavModule,
    MatListModule,
    MatProgressBarModule,
    AsyncPipe
  ],
  template: `
    <mat-sidenav-container class="app-container">
      <mat-sidenav mode="side" opened class="sidenav">
        <div class="sidenav-header">
          <mat-icon class="logo-icon">business</mat-icon>
          <span class="logo-text">Insurance Portal</span>
        </div>
        <mat-nav-list>
          <a mat-list-item routerLink="/dashboard" routerLinkActive="active">
            <mat-icon matListItemIcon>dashboard</mat-icon>
            <span matListItemTitle>Dashboard</span>
          </a>
          <a mat-list-item routerLink="/claims" routerLinkActive="active" [routerLinkActiveOptions]="{exact: false}">
            <mat-icon matListItemIcon>description</mat-icon>
            <span matListItemTitle>Claims</span>
          </a>
        </mat-nav-list>
      </mat-sidenav>

      <mat-sidenav-content>
        @if (loadingService.loading$ | async) {
          <mat-progress-bar mode="indeterminate" class="global-loading"></mat-progress-bar>
        }
        <main class="main-content">
          <router-outlet></router-outlet>
        </main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    .app-container {
      height: 100vh;
    }

    .sidenav {
      width: 240px;
      background: #fff;
      border-right: 1px solid #e0e0e0;
    }

    .sidenav-header {
      display: flex;
      align-items: center;
      padding: 16px;
      border-bottom: 1px solid #e0e0e0;
    }

    .logo-icon {
      color: #1976d2;
      margin-right: 8px;
    }

    .logo-text {
      font-size: 18px;
      font-weight: 500;
      color: #333;
    }

    mat-nav-list a.active {
      background-color: rgba(25, 118, 210, 0.1);
      color: #1976d2;
    }

    .main-content {
      padding: 24px;
      background-color: #f5f5f5;
      min-height: calc(100vh - 48px);
    }

    .global-loading {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      z-index: 1000;
    }
  `]
})
export class AppComponent {
  constructor(public loadingService: LoadingService) {}
}
