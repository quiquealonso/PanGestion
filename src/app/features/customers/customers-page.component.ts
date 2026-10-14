import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Company } from '../../core/models/company.model';
import { Customer } from '../../core/models/customer.model';
import { Order } from '../../core/models/order.model';
import { AuthService } from '../../core/services/auth.service';
import { CompaniesService } from '../../core/services/companies.service';
import { CustomersService } from '../../core/services/customers.service';
import { NotificationService } from '../../core/services/notification.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { OrderStatusPipe } from '../../shared/order-status.pipe';

@Component({
  imports: [CurrencyPipe, DatePipe, FormsModule, ReactiveFormsModule, RouterLink, OrderStatusPipe],
  selector: 'app-customers-page',
  templateUrl: './customers-page.component.html',
  styles: '.history { margin-top: 1.4rem; }',
})
export class CustomersPageComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly customersService = inject(CustomersService);
  private readonly companiesService = inject(CompaniesService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);

  readonly customers = signal<Customer[]>([]);
  readonly companies = signal<Company[]>([]);
  readonly orders = signal<Order[]>([]);
  readonly selectedId = signal<number | null>(null);
  readonly selected = computed(() => this.customers().find((c) => c.id === this.selectedId()) ?? null);
  readonly totalSpent = computed(() =>
    this.orders().filter((o) => o.status !== 'CANCELADO').reduce((sum, o) => sum + o.total, 0),
  );
  search = '';

  readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    companyId: [null as number | null],
  });

  ngOnInit(): void {
    if (!this.auth.canEdit()) this.form.disable();
    this.companiesService.list().subscribe((companies) => this.companies.set(companies));
    this.load();
  }

  load(): void {
    this.customersService.search(this.search).subscribe({
      next: (customers) => this.customers.set(customers),
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  select(customer: Customer): void {
    this.selectedId.set(customer.id);
    this.form.reset({ name: customer.name, email: customer.email, phone: customer.phone ?? '', companyId: customer.companyId });
    this.customersService.orders(customer.id).subscribe((orders) => this.orders.set(orders));
  }

  startCreate(): void {
    this.selectedId.set(null);
    this.orders.set([]);
    this.form.reset({ name: '', email: '', phone: '', companyId: null });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const body = { name: value.name!, email: value.email!, phone: value.phone || null, companyId: value.companyId };
    const id = this.selectedId();
    const request = id ? this.customersService.update(id, body) : this.customersService.create(body);
    request.subscribe({
      next: (saved) => {
        this.notify.success(`Cliente "${saved.name}" guardado`);
        this.selectedId.set(saved.id);
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  remove(): void {
    const customer = this.selected();
    if (!customer || !confirm(`¿Eliminar el cliente "${customer.name}"?`)) return;
    this.customersService.remove(customer.id).subscribe({
      next: () => {
        this.notify.success('Cliente eliminado');
        this.startCreate();
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }
}
