import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { environment } from '../../../environments/environment';
import { APP_VERSION } from '../../../environments/version';

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-auth-page',
  styleUrl: './auth-page.component.scss',
  templateUrl: './auth-page.component.html',
})
export class AuthPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);
  /** Usuarios de prueba (solo en desarrollo, ver environment.ts). */
  protected readonly testUsers = environment.testUsers;
  protected readonly version = APP_VERSION;
  protected readonly copied = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  constructor() {
    if (this.auth.isLoggedIn()) {
      this.router.navigate([this.auth.isSuperAdmin() ? '/companies' : '/']);
    }
  }

  /** Rellena el formulario con un usuario de prueba. */
  useTestUser(user: { email: string; password: string }): void {
    this.form.setValue({ email: user.email, password: user.password });
  }

  async copy(text: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(text);
      this.copied.set(text);
      setTimeout(() => this.copied.set(null), 1500);
    } catch {
      // Sin permiso de portapapeles: no pasa nada, se puede usar el boton "Usar"
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, password } = this.form.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(email, password).subscribe({
      // "/" lleva a la pantalla de cada rol: el camarero entra directo a las mesas, cocina a cocina, etc.
      next: () => this.router.navigate([this.auth.isSuperAdmin() ? '/companies' : '/']),
      error: (err) => {
        this.error.set(apiErrorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
