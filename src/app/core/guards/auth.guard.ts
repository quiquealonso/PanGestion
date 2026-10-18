import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Solo usuarios con sesion iniciada. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? true : inject(Router).createUrlTree(['/auth/login']);
};

/** Gestion de usuarios: SUPERADMIN o ADMIN. */
export const usersGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.canManageUsers() ? true : inject(Router).createUrlTree(['/dashboard']);
};

/** Pantallas solo del SUPERADMIN (alta de empresas). */
export const superAdminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isSuperAdmin() ? true : inject(Router).createUrlTree(['/dashboard']);
};

/** Pantallas de trabajo de una empresa: el SUPERADMIN no pertenece a ninguna, se le lleva a Empresas. */
export const companyGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isSuperAdmin() ? inject(Router).createUrlTree(['/companies']) : true;
};
