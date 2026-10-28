import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginResponse, User } from '../models/user.model';

const TOKEN_KEY = 'pangestion.token';
const USER_KEY = 'pangestion.user';

/** Sesion del usuario: guarda el token JWT y expone el usuario y sus permisos. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly userSignal = signal<User | null>(this.readStoredUser());

  readonly user = this.userSignal.asReadonly();
  readonly isLoggedIn = computed(() => this.userSignal() !== null);
  /** Gestiona las empresas; no pertenece a ninguna. */
  readonly isSuperAdmin = computed(() => this.userSignal()?.role === 'SUPERADMIN');
  /** Administrador de su empresa. */
  readonly isAdmin = computed(() => this.userSignal()?.role === 'ADMIN');
  /** Puede gestionar usuarios (SUPERADMIN todos, ADMIN los de su empresa). */
  readonly canManageUsers = computed(() => this.isSuperAdmin() || this.isAdmin());
  /** ADMIN y GESTOR pueden crear, editar y borrar; VISOR solo consulta. */
  readonly canEdit = computed(() => {
    const role = this.userSignal()?.role;
    return role === 'ADMIN' || role === 'GESTOR';
  });

  /** Bares y restaurantes tienen plano de mesas. */
  readonly hasDining = computed(() => {
    const type = this.userSignal()?.companyType;
    return type === 'BAR' || type === 'RESTAURANTE';
  });

  /** Camarero: solo trabaja en el servicio de mesas. */
  readonly isWaiter = computed(() => this.userSignal()?.role === 'CAMARERO');
  /** Puede abrir mesas y tomar comandas (bares y restaurantes). */
  readonly canServe = computed(() => {
    const role = this.userSignal()?.role;
    return this.hasDining() && (role === 'ADMIN' || role === 'GESTOR' || role === 'CAMARERO');
  });

  /** Cocina: solo trabaja en la pantalla de cocina y barra. */
  readonly isKitchen = computed(() => this.userSignal()?.role === 'COCINA');
  /** Repartidor: solo ve los pedidos a domicilio */
  readonly isDriver = computed(() => this.userSignal()?.role === 'REPARTIDOR');
  /** Pantalla de reparto: repartidores, gestores y admins */
  readonly canDeliver = computed(() => {
    const role = this.userSignal()?.role;
    return role === 'REPARTIDOR' || role === 'ADMIN' || role === 'GESTOR';
  });
  /** Puede usar la pantalla de cocina y barra (bares y restaurantes). */
  readonly canKitchen = computed(() => {
    const role = this.userSignal()?.role;
    return this.hasDining() && (role === 'ADMIN' || role === 'GESTOR' || role === 'COCINA');
  });

  /** Puede ver los informes de sala (la facturacion): admins, gestores y visores de bares y restaurantes. */
  readonly canReport = computed(() => {
    const role = this.userSignal()?.role;
    return this.hasDining() && (role === 'ADMIN' || role === 'GESTOR' || role === 'VISOR');
  });

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${environment.apiBaseUrl}/auth/login`, { email, password }).pipe(
      tap((response) => {
        localStorage.setItem(TOKEN_KEY, response.token);
        localStorage.setItem(USER_KEY, JSON.stringify(response.user));
        this.userSignal.set(response.user);
      }),
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.userSignal.set(null);
    this.router.navigate(['/auth/login']);
  }

  token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  private readStoredUser(): User | null {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw && localStorage.getItem(TOKEN_KEY) ? (JSON.parse(raw) as User) : null;
    } catch {
      return null;
    }
  }
}
