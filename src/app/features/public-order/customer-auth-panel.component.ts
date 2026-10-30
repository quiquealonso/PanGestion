import { Component, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { CustomerAuthService } from '../../core/services/customer-auth.service';
import { apiErrorMessage } from '../../core/utils/api-error';

type Mode = 'login' | 'register' | 'check-email';

/**
 * Acceso del cliente en la web publica: "Entrar" o "Crear cuenta". Para hacer un pedido hace falta cuenta.
 * Al crearla se envia un email con un enlace para confirmarla; hasta entonces no se puede entrar.
 */
@Component({
  selector: 'app-customer-auth-panel',
  imports: [FormsModule],
  template: `
    <section class="cap" aria-labelledby="cap-title">
      @if (mode() === 'check-email') {
        <h3 id="cap-title">📧 Revisa tu correo</h3>
        <p>{{ info() }}</p>
        <p class="cap__muted">Te lo hemos enviado a <b>{{ email }}</b>. Si no aparece, mira en «Spam» o «Promociones».</p>
        <div class="cap__row">
          <button type="button" class="cap__secondary" (click)="resend()" [disabled]="busy()">Enviar otro enlace</button>
          <button type="button" class="cap__link" (click)="mode.set('login')">Ya la he confirmado: entrar</button>
        </div>
      } @else {
        <h3 id="cap-title">Para pedir, entra en tu cuenta</h3>
        <div class="cap__tabs" role="tablist">
          <button type="button" role="tab" [attr.aria-selected]="mode() === 'login'" [class.is-active]="mode() === 'login'"
                  (click)="switchTo('login')">Entrar</button>
          <button type="button" role="tab" [attr.aria-selected]="mode() === 'register'" [class.is-active]="mode() === 'register'"
                  (click)="switchTo('register')">Crear cuenta</button>
        </div>

        <form class="cap__form" (ngSubmit)="submit()" novalidate>
          @if (mode() === 'register') {
            <label class="cap__field">
              <span>Nombre y apellido</span>
              <input name="name" autocomplete="name" [(ngModel)]="name" placeholder="Por ejemplo: María García" required />
            </label>
          }
          <label class="cap__field">
            <span>Email</span>
            <input name="email" type="email" inputmode="email" autocomplete="email" [(ngModel)]="email"
                   placeholder="Por ejemplo: maria@gmail.com" required />
          </label>
          <label class="cap__field">
            <span>Contraseña @if (mode() === 'register') { <small>(mínimo 8 caracteres)</small> }</span>
            <input name="password" type="password" [attr.autocomplete]="mode() === 'register' ? 'new-password' : 'current-password'"
                   [(ngModel)]="password" required />
          </label>
          @if (mode() === 'register') {
            <label class="cap__field">
              <span>Teléfono <small>(opcional, para avisarte)</small></span>
              <input name="phone" type="tel" inputmode="tel" autocomplete="tel" [(ngModel)]="phone" placeholder="Por ejemplo: 612 345 678" />
            </label>
          }

          @if (error()) {
            <p class="cap__error" role="alert">{{ error() }}</p>
            @if (unverified()) {
              <button type="button" class="cap__link" (click)="resend()" [disabled]="busy()">Enviarme otro enlace de confirmación</button>
            }
          }

          <button type="submit" class="cap__primary" [disabled]="busy()">
            {{ busy() ? 'Un momento…' : mode() === 'login' ? 'Entrar' : 'Crear cuenta' }}
          </button>
        </form>
      }
    </section>
  `,
  // Usa la paleta clara de la web publica (--p-*, definida en public-order-page)
  styles: `
    .cap { display: grid; gap: 14px; padding: 18px; border: 1px solid var(--p-border); border-radius: 18px; background: var(--p-card); color: var(--p-text); }
    h3 { margin: 0; font-size: 1.15rem; font-family: 'Space Grotesk', system-ui, sans-serif; }
    p { margin: 0; }
    .cap__muted { color: var(--p-muted); font-size: 0.95rem; }
    .cap__tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; padding: 4px; border-radius: 14px; background: var(--p-bg); }
    .cap__tabs button { min-height: 48px; border: 0; border-radius: 11px; font: inherit; font-weight: 600; background: transparent; color: var(--p-muted); cursor: pointer; }
    .cap__tabs button.is-active { background: var(--p-card); color: var(--p-text); box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12); }
    .cap__form { display: grid; gap: 14px; }
    .cap__field { display: grid; gap: 6px; font-weight: 600; }
    .cap__field small { font-weight: 400; color: var(--p-muted); }
    .cap__field input { min-height: 48px; font: inherit; padding: 10px 12px; border-radius: 12px; border: 2px solid var(--p-border); background: var(--p-card); color: var(--p-text); }
    .cap__field input:focus { outline: 3px solid var(--p-focus); outline-offset: 1px; }
    .cap__error { color: var(--p-danger); font-weight: 600; }
    .cap__row { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
    .cap__primary, .cap__secondary { min-height: 52px; border: 0; border-radius: 14px; padding: 12px 18px; font: inherit; font-weight: 700; cursor: pointer; }
    .cap__primary { background: var(--p-primary); color: #fff; }
    .cap__primary:hover { background: var(--p-primary-dark); }
    .cap__secondary { background: var(--p-primary-soft); color: var(--p-primary-dark); }
    .cap__link { justify-self: start; min-height: 44px; border: 0; padding: 4px 0; background: none; color: var(--p-primary-dark); font: inherit; font-weight: 600; text-decoration: underline; cursor: pointer; }
    button:disabled { opacity: 0.6; cursor: progress; }
  `,
})
export class CustomerAuthPanelComponent {
  private readonly auth = inject(CustomerAuthService);
  /** Sesion iniciada (al entrar, o al crear la cuenta si el servidor no pide confirmar el email) */
  readonly loggedIn = output<void>();

  protected readonly mode = signal<Mode>('login');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal('');
  /** El login fallo porque la cuenta aun no ha confirmado el email */
  protected readonly unverified = signal(false);

  protected name = '';
  protected email = '';
  protected password = '';
  protected phone = '';

  protected switchTo(mode: Mode): void {
    this.mode.set(mode);
    this.error.set(null);
    this.unverified.set(false);
  }

  protected submit(): void {
    const problem = this.validate();
    if (problem) {
      this.error.set(problem);
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.unverified.set(false);

    if (this.mode() === 'login') {
      this.auth.login(this.email.trim(), this.password).subscribe({
        next: () => this.done(),
        error: (err: HttpErrorResponse) => {
          this.busy.set(false);
          this.unverified.set(err.status === 403);
          this.error.set(err.status === 401 ? 'Email o contraseña incorrectos.' : apiErrorMessage(err));
        },
      });
    } else {
      this.auth
        .register({ name: this.name.trim(), email: this.email.trim(), password: this.password, phone: this.phone.trim() || null })
        .subscribe({
          next: (response) => {
            this.busy.set(false);
            if (response.session) {
              this.done();
            } else {
              this.info.set(response.message);
              this.mode.set('check-email');
            }
          },
          error: (err) => {
            this.busy.set(false);
            this.error.set(apiErrorMessage(err));
          },
        });
    }
  }

  protected resend(): void {
    if (!/^\S+@\S+\.\S+$/.test(this.email.trim())) {
      this.error.set('Escribe tu email para enviarte otro enlace.');
      return;
    }
    this.busy.set(true);
    this.auth.resend(this.email.trim()).subscribe({
      next: (response) => {
        this.busy.set(false);
        this.info.set(response.message);
        this.mode.set('check-email');
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(apiErrorMessage(err));
      },
    });
  }

  private done(): void {
    this.busy.set(false);
    this.password = '';
    this.loggedIn.emit();
  }

  private validate(): string | null {
    if (this.mode() === 'register' && this.name.trim().length < 2) return 'Escribe tu nombre.';
    if (!/^\S+@\S+\.\S+$/.test(this.email.trim())) return 'Escribe un email válido, por ejemplo: nombre@gmail.com';
    if (this.mode() === 'register' && this.password.length < 8) return 'La contraseña debe tener al menos 8 caracteres.';
    if (!this.password) return 'Escribe tu contraseña.';
    if (this.mode() === 'register' && this.phone.trim() && !/^[0-9 +]{9,20}$/.test(this.phone.trim())) {
      return 'Escribe un teléfono de 9 cifras, por ejemplo: 612 345 678';
    }
    return null;
  }
}
