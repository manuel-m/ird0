import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const roleGuard = (requiredRole: string): CanActivateFn => {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    // Role hierarchy is handled by Keycloak composite roles:
    // claims-admin includes claims-manager, which includes claims-viewer
    if (authService.hasRole(requiredRole)) {
      return true;
    }
    return router.createUrlTree(['/dashboard']);
  };
};
