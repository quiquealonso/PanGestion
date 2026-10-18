import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Customer } from '../../core/models/customer.model';
import { Order } from '../../core/models/order.model';
import { AuthService } from '../../core/services/auth.service';
import { CustomersService } from '../../core/services/customers.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NotificationService } from '../../core/services/notification.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { ModalComponent } from '../../shared/modal/modal.component';
import { OrderStatusPipe } from '../../shared/order-status.pipe';

@Component({
  imports: [CurrencyPipe, DatePipe, FormsModule, ReactiveFormsModule, RouterLink, OrderStatusPipe, ModalComponent],
  selector: 'app-customers-page',
  templateUrl: './customers-page.component.html',
  styles: '.history { margin-top: 1.4rem; } .history .list { max-height: 260px; overflow: auto; }',
})
export class CustomersPageComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly customersService = inject(CustomersService);
  private readonly notify = inject(NotificationService);
  private readonly confirmService = inject(ConfirmService);
  protected readonly auth = inject(AuthService);

  readonly customers = signal<Customer[]>([]);
  readonly orders = signal<Order[]>([]);
  readonly selectedId = signal<number | null>(null);
  readonly dialogOpen = signal(false);
  readonly selected = computed(() => this.customers().find((c) => c.id === this.selectedId()) ?? null);
  readonly totalSpent = computed(() =>
    this.orders().filter((o) => o.status !== 'CANCELADO').reduce((sum, o) => sum + o.total, 0),
  );
  search = '';
  /** Negocios (con razon social) y particulares, para el resumen de arriba. */
  readonly businessCount = computed(() => this.customers().filter((c) => c.businessName).length);

  readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    businessName: [''],
    taxId: [''],
  });

  ngOnInit(): void {
    if (!this.auth.canEdit()) this.form.disable();
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
    this.form.reset({ name: customer.name, email: customer.email, phone: customer.phone ?? '', businessName: customer.businessName ?? '', taxId: customer.taxId ?? '' });
    this.orders.set([]);
    this.customersService.orders(customer.id).subscribe((orders) => this.orders.set(orders));
    this.dialogOpen.set(true);
  }

  startCreate(): void {
    this.selectedId.set(null);
    this.orders.set([]);
    this.form.reset({ name: '', email: '', phone: '', businessName: '', taxId: '' });
    this.dialogOpen.set(true);
  }

  closeDialog(): void {
    this.dialogOpen.set(false);
    this.selectedId.set(null);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const body = {
      name: value.name!,
      email: value.email!,
      phone: value.phone || null,
      businessName: value.businessName || null,
      taxId: value.taxId || null,
    };
    const id = this.selectedId();
    const request = id ? this.customersService.update(id, body) : this.customersService.create(body);
    request.subscribe({
      next: (saved) => {
        this.notify.success(`Cliente "${saved.name}" guardado`);
        this.closeDialog();
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  async remove(): Promise<void> {
    const customer = this.selected();
    if (!customer) return;
    const accepted = await this.confirmService.ask({
      title: 'Eliminar cliente',
      message: `¿Seguro que quieres eliminar "${customer.name}"? Esta acción no se puede deshacer.`,
    });
    if (!accepted) return;
    this.customersService.remove(customer.id).subscribe({
      next: () => {
        this.notify.success('Cliente eliminado');
        this.closeDialog();
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }
}
