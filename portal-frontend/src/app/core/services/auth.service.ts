import { computed, Injectable, signal } from '@angular/core';
import { OAuthService, AuthConfig } from 'angular-oauth2-oidc';
import { environment } from '../../../environments/environment';

export interface UserProfile {
  sub: string;
  name?: string;
  preferred_username?: string;
  email?: string;
  given_name?: string;
  family_name?: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private _isAuthenticated = signal<boolean>(false);
  private _userProfile = signal<UserProfile | null>(null);

  readonly isAuthenticated = this._isAuthenticated.asReadonly();
  readonly userProfile = this._userProfile.asReadonly();
  readonly userName = computed(() => {
    const profile = this._userProfile();
    return profile?.name || profile?.preferred_username || 'Unknown User';
  });
  readonly isManager = computed(() => {
    if (!this._isAuthenticated()) return false;
    return this.hasRole('claims-manager') || this.hasRole('claims-admin');
  });
  readonly isAdmin = computed(() => {
    if (!this._isAuthenticated()) return false;
    return this.hasRole('claims-admin');
  });

  constructor(private oauthService: OAuthService) {}

  async configure(): Promise<void> {
    const authConfig: AuthConfig = {
      issuer: environment.auth.issuer,
      clientId: environment.auth.clientId,
      redirectUri: environment.auth.redirectUri,
      scope: environment.auth.scope,
      responseType: environment.auth.responseType,
      requireHttps: environment.auth.requireHttps,
      showDebugInformation: environment.auth.showDebugInformation,
      silentRefreshRedirectUri: window.location.origin + '/silent-refresh.html',
      useSilentRefresh: true,
      silentRefreshTimeout: 5000,
      timeoutFactor: 0.75,
    };

    this.oauthService.configure(authConfig);

    this.oauthService.events.subscribe(event => {
      if (event.type === 'token_received' || event.type === 'token_refreshed') {
        this.updateAuthState();
      }
      if (event.type === 'logout' || event.type === 'session_terminated') {
        this._isAuthenticated.set(false);
        this._userProfile.set(null);
      }
    });

    try {
      await this.oauthService.loadDiscoveryDocumentAndTryLogin();
      this.updateAuthState();

      if (this.oauthService.hasValidAccessToken()) {
        this.oauthService.setupAutomaticSilentRefresh();
      }
    } catch (error) {
      console.error('Failed to initialize OAuth:', error);
    }
  }

  private updateAuthState(): void {
    const hasValidToken = this.oauthService.hasValidAccessToken();
    this._isAuthenticated.set(hasValidToken);

    if (hasValidToken) {
      const claims = this.oauthService.getIdentityClaims() as UserProfile;
      this._userProfile.set(claims);
    } else {
      this._userProfile.set(null);
    }
  }

  login(): void {
    this.oauthService.initCodeFlow();
  }

  logout(): void {
    this.oauthService.logOut();
  }

  getAccessToken(): string | null {
    return this.oauthService.getAccessToken();
  }

  hasRole(role: string): boolean {
    const accessToken = this.oauthService.getAccessToken();
    if (!accessToken) {
      return false;
    }

    try {
      const payload = JSON.parse(atob(accessToken.split('.')[1]));
      const clientRoles = payload?.resource_access?.['ird0-portal-bff']?.roles;
      return Array.isArray(clientRoles) && clientRoles.includes(role);
    } catch {
      return false;
    }
  }
}
