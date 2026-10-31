import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CustomerAccount, CustomerSession, RegisterRequest, RegisterResponse } from '../models/customer-account.model';

const SESSION_KEY = 'pangestion.customer-session';

/**
 * Sesion del cliente en la web publica de pedidos (distinta de la de gestion: otro token, rol CLIENTE).
 * Para hacer un pedido hay que tener cuenta con el email confirmado.
 */
@Injectable({ providedIn: 'root' })
export class CustomerAuthService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/public/account`;
  private readonly session = signal<CustomerSession | null>(readSession());

  readonly account = computed(() => this.session()?.account ?? null);
  readonly isLoggedIn = computed(() => this.session() !== null);

  token(): string | null {
    return this.session()?.token ?? null;
  }

  register(body: RegisterRequest): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>(`${this.url}/register`, body).pipe(
      tap((response) => response.session && this.save(response.session)),
    );
  }

  login(email: string, password: string): Observable<CustomerSession> {
    return this.http.post<CustomerSession>(`${this.url}/login`, { email, password }).pipe(tap((s) => this.save(s)));
  }

  /** Enlace del email de confirmacion (?verificar=...): activa la cuenta y deja la sesion iniciada. */
  verify(token: string): Observable<CustomerSession> {
    return this.http.post<CustomerSession>(`${this.url}/verify`, { token }).pipe(tap((s) => this.save(s)));
  }

  resend(email: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.url}/resend`, { email });
  }

  /** Datos actuales de la cuenta (y comprueba que la sesion sigue valida: si no, el interceptor la cierra). */
  refresh(): void {
    if (!this.session()) return;
    this.http.get<CustomerAccount>(`${this.url}/me`).subscribe({
      next: (account) => {
        const current = this.session();
        if (current) this.save({ ...current, account });
      },
      error: () => undefined,
    });
  }

  logout(): void {
    this.session.set(null);
    try {
      localStorage.removeItem(SESSION_KEY);
    } catch {
      // Sin almacenamiento: la sesion solo vivia en memoria
    }
  }

  private save(session: CustomerSession): void {
    this.session.set(session);
    try {
      localStorage.setItem(SESSION_KEY, JSON.stringify(session));
    } catch {
      // Sin almacenamiento (modo privado): la sesion dura hasta cerrar la pagina
    }
  }
}

function readSession(): CustomerSession | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    return raw ? (JSON.parse(raw) as CustomerSession) : null;
  } catch {
    return null;
  }
}
