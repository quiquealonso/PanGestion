import { inject } from '@angular/core';
import { CanActivateChildFn, CanActivateFn, Router } from '@angular/router';
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

/** Plano de mesas: solo bares y restaurantes. */
export const diningGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.hasDining() ? true : inject(Router).createUrlTree(['/dashboard']);
};

/** Servicio de mesas: camareros, gestores y admins de bares y restaurantes. */
export const serviceGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.canServe() ? true : inject(Router).createUrlTree(['/dashboard']);
};

/** Barra y cobros (plano para cobrar y caja del dia): gestores y admins. El camarero cobra desde la mesa. */
export const barGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.isWaiter()) return inject(Router).createUrlTree(['/service']);
  return auth.canServe() ? true : inject(Router).createUrlTree(['/dashboard']);
};

/** Pantalla de cocina y barra: cocina, gestores y admins de bares y restaurantes. */
export const kitchenGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.canKitchen() ? true : inject(Router).createUrlTree(['/dashboard']);
};

/** Informes de sala: admins, gestores y visores de bares y restaurantes. */
export const reportGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.canReport() ? true : inject(Router).createUrlTree(['/dashboard']);
};

/** Pantallas de trabajo de una empresa: el SUPERADMIN no pertenece a ninguna, se le lleva a Empresas. */
export const companyGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isSuperAdmin() ? inject(Router).createUrlTree(['/companies']) : true;
};

/** Cocina y repartidor solo usan su pantalla: cualquier otra les lleva a la suya. */
export const kitchenOnlyGuard: CanActivateChildFn = (route) => {
  const auth = inject(AuthService);
  const path = route.routeConfig?.path;
  if (auth.isKitchen() && path !== 'kitchen') return inject(Router).createUrlTree(['/kitchen']);
  if (auth.isDriver() && path !== 'delivery') return inject(Router).createUrlTree(['/delivery']);
  return true;
};

/** Pantalla de reparto: repartidores, gestores y admins. */
export const deliveryGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.canDeliver() ? true : inject(Router).createUrlTree(['/dashboard']);
};
