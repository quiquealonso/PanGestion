import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Company } from '../../core/models/company.model';
import { AuthService } from '../../core/services/auth.service';
import { CompaniesService } from '../../core/services/companies.service';
import { NotificationService } from '../../core/services/notification.service';
import { apiErrorMessage } from '../../core/utils/api-error';

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-companies-page',
  templateUrl: './companies-page.component.html',
})
export class CompaniesPageComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly companiesService = inject(CompaniesService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);

  readonly companies = signal<Company[]>([]);
  readonly selectedId = signal<number | null>(null);
  readonly selected = computed(() => this.companies().find((c) => c.id === this.selectedId()) ?? null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    taxId: ['', [Validators.required, Validators.maxLength(20)]],
    email: ['', Validators.email],
    phone: [''],
    address: [''],
  });

  ngOnInit(): void {
    if (!this.auth.canEdit()) this.form.disable();
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
    this.form.reset({
      name: company.name,
      taxId: company.taxId,
      email: company.email ?? '',
      phone: company.phone ?? '',
      address: company.address ?? '',
    });
  }

  startCreate(): void {
    this.selectedId.set(null);
    this.form.reset({ name: '', taxId: '', email: '', phone: '', address: '' });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const body = { ...value, email: value.email || null, phone: value.phone || null, address: value.address || null };
    const id = this.selectedId();
    const request = id ? this.companiesService.update(id, body) : this.companiesService.create(body);
    request.subscribe({
      next: (saved) => {
        this.notify.success(`Empresa "${saved.name}" guardada`);
        this.selectedId.set(saved.id);
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  remove(): void {
    const company = this.selected();
    if (!company || !confirm(`¿Eliminar la empresa "${company.name}"?`)) return;
    this.companiesService.remove(company.id).subscribe({
      next: () => {
        this.notify.success('Empresa eliminada');
        this.startCreate();
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }
}
