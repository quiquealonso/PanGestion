import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { BUSINESS_TYPE_LABELS, Company, CompanyRequest } from '../../core/models/company.model';
import { CompaniesService } from '../../core/services/companies.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NotificationService } from '../../core/services/notification.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { CompanyFormComponent } from '../../shared/company-form/company-form.component';
import { ModalComponent } from '../../shared/modal/modal.component';

/** Alta y gestion de empresas (panaderias). Solo SUPERADMIN. */
@Component({
  imports: [CompanyFormComponent, ModalComponent],
  selector: 'app-companies-page',
  templateUrl: './companies-page.component.html',
})
export class CompaniesPageComponent implements OnInit {
  private readonly companiesService = inject(CompaniesService);
  private readonly notify = inject(NotificationService);
  private readonly confirmService = inject(ConfirmService);

  readonly typeLabels = BUSINESS_TYPE_LABELS;
  readonly companies = signal<Company[]>([]);
  readonly selectedId = signal<number | null>(null);
  readonly dialogOpen = signal(false);
  readonly selected = computed(() => this.companies().find((c) => c.id === this.selectedId()) ?? null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.companiesService.list().subscribe({
      next: (companies) => this.companies.set(companies),
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  select(company: Company): void {
    this.selectedId.set(company.id);
    this.dialogOpen.set(true);
  }

  startCreate(): void {
    this.selectedId.set(null);
    this.dialogOpen.set(true);
  }

  closeDialog(): void {
    this.dialogOpen.set(false);
    this.selectedId.set(null);
  }

  save(body: CompanyRequest): void {
    const id = this.selectedId();
    const request = id ? this.companiesService.update(id, body) : this.companiesService.create(body);
    request.subscribe({
      next: (saved) => {
        this.notify.success(id ? `Empresa "${saved.name}" guardada` : `Empresa "${saved.name}" creada. Ahora crea su administrador en Usuarios.`);
        this.closeDialog();
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  async remove(): Promise<void> {
    const company = this.selected();
    if (!company) return;
    const accepted = await this.confirmService.ask({
      title: 'Eliminar empresa',
      message: `¿Seguro que quieres eliminar "${company.name}"? Solo se puede si no tiene usuarios ni pedidos.`,
    });
    if (!accepted) return;
    this.companiesService.remove(company.id).subscribe({
      next: () => {
        this.notify.success('Empresa eliminada');
        this.closeDialog();
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }
}
