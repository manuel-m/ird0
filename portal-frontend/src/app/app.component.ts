import { Component, computed } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatMenuModule } from '@angular/material/menu';
import { LoadingService } from './core/services/loading.service';
import { AuthService } from './core/services/auth.service';
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
    MatMenuModule,
    AsyncPipe
  ],
  template: `
    @if (authService.isAuthenticated()) {
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
          <div class="sidenav-footer">
            <div class="user-profile" [matMenuTriggerFor]="userMenu">
              <div class="avatar">{{ userInitials() }}</div>
              <div class="user-info">
                <span class="user-name">{{ authService.userName() }}</span>
                <span class="user-email">{{ userEmail() }}</span>
              </div>
              <mat-icon>expand_more</mat-icon>
            </div>
            <mat-menu #userMenu="matMenu">
              <button mat-menu-item (click)="logout()">
                <mat-icon>logout</mat-icon>
                <span>Logout</span>
              </button>
            </mat-menu>
          </div>
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
    } @else {
      <div class="loading-container">
        <mat-progress-bar mode="indeterminate"></mat-progress-bar>
        <p>Authenticating...</p>
      </div>
    }
  `,
  styles: [`
    .app-container {
      height: 100vh;
    }

    .sidenav {
      width: 240px;
      background: #fff;
      border-right: 1px solid #e0e0e0;
      display: flex;
      flex-direction: column;
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

    mat-nav-list {
      flex: 1;
    }

    mat-nav-list a.active {
      background-color: rgba(25, 118, 210, 0.1);
      color: #1976d2;
    }

    .sidenav-footer {
      border-top: 1px solid #e0e0e0;
      padding: 8px;
    }

    .user-profile {
      display: flex;
      align-items: center;
      padding: 8px;
      border-radius: 8px;
      cursor: pointer;
      transition: background-color 0.2s;
    }

    .user-profile:hover {
      background-color: rgba(0, 0, 0, 0.04);
    }

    .avatar {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background-color: #1976d2;
      color: white;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 500;
      font-size: 14px;
      margin-right: 12px;
    }

    .user-info {
      flex: 1;
      min-width: 0;
    }

    .user-name {
      display: block;
      font-weight: 500;
      font-size: 14px;
      color: #333;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .user-email {
      display: block;
      font-size: 12px;
      color: #666;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
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

    .loading-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 100vh;
      gap: 16px;
    }

    .loading-container mat-progress-bar {
      width: 200px;
    }

    .loading-container p {
      color: #666;
    }
  `]
})
export class AppComponent {
  constructor(
    public loadingService: LoadingService,
    public authService: AuthService
  ) {}

  userInitials = computed(() => {
    const profile = this.authService.userProfile();
    if (profile?.given_name && profile?.family_name) {
      return (profile.given_name[0] + profile.family_name[0]).toUpperCase();
    }
    const name = this.authService.userName();
    return name.substring(0, 2).toUpperCase();
  });

  userEmail = computed(() => {
    const profile = this.authService.userProfile();
    return profile?.email || '';
  });

  logout(): void {
    this.authService.logout();
  }
}
