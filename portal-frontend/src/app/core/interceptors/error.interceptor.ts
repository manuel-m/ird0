import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, EMPTY, throwError } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../services/auth.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const snackBar = inject(MatSnackBar);

  const showError = (message: string) => {
    snackBar.open(message, 'Dismiss', {
      duration: 5000,
      panelClass: ['error-snackbar'],
      horizontalPosition: 'center',
      verticalPosition: 'bottom',
    });
  };

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      let errorMessage = 'An unexpected error occurred';

      if (error.error instanceof ErrorEvent) {
        // Client-side error
        errorMessage = `Error: ${error.error.message}`;
      } else {
        // Server-side error
        if (error.status === 0) {
          errorMessage =
            'Unable to connect to the server. Please check your connection.';
        } else if (error.status === 401) {
          // Trigger re-authentication on 401 and prevent error propagation
          authService.login();
          return EMPTY;
        } else if (error.status === 403) {
          errorMessage =
            'Access denied. You do not have permission to perform this action.';
        } else if (error.status === 404) {
          errorMessage = 'Resource not found.';
        } else if (error.status === 503) {
          errorMessage =
            'Service temporarily unavailable. Please try again later.';
        } else if (error.error?.message) {
          errorMessage = error.error.message;
        } else {
          errorMessage = `Server error: ${error.status}`;
        }
      }

      console.error('HTTP Error:', errorMessage, error);
      showError(errorMessage);
      return throwError(() => new Error(errorMessage));
    })
  );
};
