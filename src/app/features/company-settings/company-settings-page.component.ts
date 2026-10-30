import { Component, OnInit, inject, signal } from '@angular/core';
import { BUSINESS_TYPE_LABELS, Company, CompanyRequest } from '../../core/models/company.model';
import { AuthService } from '../../core/services/auth.service';
import { MyCompanyService } from '../../core/services/my-company.service';
import { NotificationService } from '../../core/services/notification.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { CompanyFormComponent } from '../../shared/company-form/company-form.component';
import { AssetUrlPipe } from '../../shared/asset-url.pipe';

/** Datos y horario de recogida de la empresa del usuario. Solo el ADMIN puede cambiarlos. */
@Component({
  imports: [CompanyFormComponent, AssetUrlPipe],
  selector: 'app-company-settings-page',
  template: `
    <section class="page">
      <header class="page-head">
        <div>
          <p class="eyebrow">Empresa</p>
          <h2>{{ company()?.name ?? 'Mi empresa' }}</h2>
          <p>
            @if (company(); as c) {
              <span class="tone tone--pendiente">{{ typeLabels[c.type] }}</span>
            }
            Datos del negocio y horario en el que los clientes pueden recoger sus pedidos.
          </p>
        </div>
      </header>
      @if (company(); as c) {
        <!-- Logo del negocio: sale arriba a la izquierda en la cabecera del camarero y del repartidor -->
        <article class="panel settings logo-panel">
          <div class="logo-box">
            @if (c.logoUrl) {
              <img [src]="c.logoUrl | assetUrl" alt="Logo de {{ c.name }}" />
            } @else {
              <span class="muted">Sin logo</span>
            }
          </div>
          <div class="logo-text">
            <h3>Logo</h3>
            <p class="muted">JPG, PNG o WEBP de hasta 2 MB. Mejor con fondo transparente y más ancho que alto.</p>
            @if (auth.isAdmin()) {
              <div class="logo-actions">
                <label class="btn btn--small">
                  {{ c.logoUrl ? 'Cambiar logo' : 'Subir logo' }}
                  <input type="file" accept="image/jpeg,image/png,image/webp" hidden (change)="onLogo($event)" />
                </label>
                @if (c.logoUrl) {
                  <button type="button" class="btn btn--small" (click)="removeLogo()">Quitar</button>
                }
              </div>
            }
          </div>
        </article>
      }
      <article class="panel settings">
        @if (company(); as c) {
          <app-company-form [company]="c" [readonly]="!auth.isAdmin()" (submitted)="save($event)" />
          @if (!auth.isAdmin()) {
            <p class="muted">Solo el administrador de la empresa puede modificar estos datos.</p>
          }
        }
      </article>
    </section>
  `,
  styles: `
    .settings {
      max-width: 720px;
    }

    .logo-panel {
      display: flex;
      align-items: center;
      gap: 1.2rem;
    }

    .logo-box {
      display: grid;
      place-items: center;
      flex: none;
      width: 10rem;
      height: 5rem;
      border: 1px dashed var(--border);
      border-radius: 0.9rem;
      background: rgba(var(--tint-rgb), 0.03);

      img {
        max-width: 92%;
        max-height: 88%;
        object-fit: contain;
      }
    }

    .logo-text {
      display: grid;
      gap: 0.4rem;

      h3,
      p {
        margin: 0;
      }
    }

    .logo-actions {
      display: flex;
      gap: 0.5rem;
    }
  `,
})
export class CompanySettingsPageComponent implements OnInit {
  private readonly myCompanyService = inject(MyCompanyService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);

  readonly typeLabels = BUSINESS_TYPE_LABELS;
  readonly company = signal<Company | null>(null);

  ngOnInit(): void {
    this.myCompanyService.get().subscribe({
      next: (company) => this.company.set(company),
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  onLogo(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.myCompanyService.uploadLogo(file).subscribe({
      next: (company) => this.logoChanged(company, 'Logo guardado'),
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  removeLogo(): void {
    this.myCompanyService.removeLogo().subscribe({
      next: (company) => this.logoChanged(company, 'Logo quitado'),
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  /** El logo va tambien en el usuario guardado (cabeceras): se refresca. */
  private logoChanged(company: Company, message: string): void {
    this.company.set(company);
    this.auth.refreshUser();
    this.notify.success(message);
  }

  save(body: CompanyRequest): void {
    this.myCompanyService.update(body).subscribe({
      next: (company) => {
        this.company.set(company);
        this.notify.success('Datos de la empresa guardados');
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }
}
