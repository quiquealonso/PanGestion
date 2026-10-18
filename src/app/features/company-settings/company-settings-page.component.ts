import { Component, OnInit, inject, signal } from '@angular/core';
import { BUSINESS_TYPE_LABELS, Company, CompanyRequest } from '../../core/models/company.model';
import { AuthService } from '../../core/services/auth.service';
import { MyCompanyService } from '../../core/services/my-company.service';
import { NotificationService } from '../../core/services/notification.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { CompanyFormComponent } from '../../shared/company-form/company-form.component';

/** Datos y horario de recogida de la empresa del usuario. Solo el ADMIN puede cambiarlos. */
@Component({
  imports: [CompanyFormComponent],
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
  styles: '.settings { max-width: 720px; }',
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
