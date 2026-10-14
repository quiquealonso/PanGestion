import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/** Anade el token JWT a las peticiones y cierra la sesion si el backend responde 401. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  const isPublic = req.url.includes('/public/') || req.url.endsWith('/auth/login');

  const request = token && !isPublic ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !isPublic) {
        auth.logout();
      }
      return throwError(() => error);
    }),
  );
};
