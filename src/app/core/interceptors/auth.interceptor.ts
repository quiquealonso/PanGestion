import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { CustomerAuthService } from '../services/customer-auth.service';

/**
 * Anade el token JWT a las peticiones y cierra la sesion si el backend responde 401.
 * Hay dos sesiones separadas: la de gestion (resto de la API) y la del cliente de la web publica (/public/).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const customer = inject(CustomerAuthService);
  const isPublic = req.url.includes('/public/');
  const isLogin = req.url.endsWith('/auth/login') || req.url.endsWith('/account/login');
  const token = isLogin ? null : isPublic ? customer.token() : auth.token();

  const request = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && token) {
        if (isPublic) customer.logout();
        else auth.logout();
      }
      return throwError(() => error);
    }),
  );
};
