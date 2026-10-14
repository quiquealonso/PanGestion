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
  readonly isAdmin = computed(() => this.userSignal()?.role === 'ADMIN');
  /** ADMIN y GESTOR pueden crear, editar y borrar; VISOR solo consulta. */
  readonly canEdit = computed(() => {
    const role = this.userSignal()?.role;
    return role === 'ADMIN' || role === 'GESTOR';
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
