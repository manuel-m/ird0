import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      let errorMessage = 'An unexpected error occurred';

      if (error.error instanceof ErrorEvent) {
        // Client-side error
        errorMessage = `Error: ${error.error.message}`;
      } else {
        // Server-side error
        if (error.status === 0) {
          errorMessage = 'Unable to connect to the server. Please check your connection.';
        } else if (error.status === 401) {
          errorMessage = 'Unauthorized. Please log in again.';
        } else if (error.status === 403) {
          errorMessage = 'Access denied. You do not have permission to perform this action.';
        } else if (error.status === 404) {
          errorMessage = 'Resource not found.';
        } else if (error.status === 503) {
          errorMessage = 'Service temporarily unavailable. Please try again later.';
        } else if (error.error?.message) {
          errorMessage = error.error.message;
        } else {
          errorMessage = `Server error: ${error.status}`;
        }
      }

      console.error('HTTP Error:', errorMessage, error);
      return throwError(() => new Error(errorMessage));
    })
  );
};
