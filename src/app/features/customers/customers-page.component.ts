import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
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
  imports: [CurrencyPipe, DatePipe, DecimalPipe, FormsModule, ReactiveFormsModule, RouterLink, OrderStatusPipe, ModalComponent],
  selector: 'app-customers-page',
  templateUrl: './customers-page.component.html',
  styleUrl: './customers-page.component.scss',
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

  // Filtros y orden del listado (en el navegador, sobre lo que devuelve la busqueda)
  readonly typeFilter = signal<TypeFilter>('');
  readonly ordersFilter = signal<RangeFilter>('');
  readonly spentFilter = signal<RangeFilter>('');
  readonly sort = signal<SortKey>('name');
  readonly ordersRanges = ORDERS_RANGES;
  readonly spentRanges = SPENT_RANGES;

  readonly filtered = computed(() => {
    const type = this.typeFilter();
    const orders = ORDERS_RANGES.find((r) => r.value === this.ordersFilter());
    const spent = SPENT_RANGES.find((r) => r.value === this.spentFilter());
    const list = this.customers().filter(
      (c) =>
        (type === '' || (type === 'business') === !!c.businessName) &&
        (!orders || inRange(c.orderCount, orders)) &&
        (!spent || inRange(c.totalSpent, spent)),
    );
    return [...list].sort(SORTERS[this.sort()]);
  });

  readonly hasFilters = computed(
    () => !!(this.typeFilter() || this.ordersFilter() || this.spentFilter()) || this.sort() !== 'name',
  );

  /** Numeros de la cabecera, calculados sobre todos los clientes de la busqueda. */
  readonly stats = computed(() => {
    const all = this.customers();
    const now = Date.now();
    const createdWithin = (days: number) => all.filter((c) => now - new Date(c.createdAt).getTime() <= days * DAY).length;
    const buyers = all.filter((c) => c.orderCount > 0);
    const orders = buyers.reduce((sum, c) => sum + c.orderCount, 0);
    const spent = buyers.reduce((sum, c) => sum + c.totalSpent, 0);
    const active = all.filter((c) => c.lastOrderDate && now - new Date(c.lastOrderDate).getTime() <= 30 * DAY).length;
    return {
      newPerWeek: createdWithin(8 * 7) / 8,
      newThisWeek: createdWithin(7),
      active,
      activePct: all.length ? Math.round((active / all.length) * 100) : 0,
      ordersPerBuyer: buyers.length ? orders / buyers.length : 0,
      withoutOrders: all.length - buyers.length,
      spentPerBuyer: buyers.length ? spent / buyers.length : 0,
      avgTicket: orders ? spent / orders : 0,
      top: buyers.length ? buyers.reduce((a, b) => (b.totalSpent > a.totalSpent ? b : a)) : null,
    };
  });

  /** En la ficha, los campos de empresa solo se ven si el cliente es un negocio. */
  readonly isBusiness = signal(false);

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
    this.isBusiness.set(!!customer.businessName);
    this.orders.set([]);
    this.customersService.orders(customer.id).subscribe((orders) => this.orders.set(orders));
    this.dialogOpen.set(true);
  }

  startCreate(): void {
    this.selectedId.set(null);
    this.orders.set([]);
    this.form.reset({ name: '', email: '', phone: '', businessName: '', taxId: '' });
    this.isBusiness.set(false);
    this.dialogOpen.set(true);
  }

  clearFilters(): void {
    this.typeFilter.set('');
    this.ordersFilter.set('');
    this.spentFilter.set('');
    this.sort.set('name');
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
    const business = this.isBusiness();
    const body = {
      name: value.name!,
      email: value.email!,
      phone: value.phone || null,
      businessName: (business && value.businessName) || null,
      taxId: (business && value.taxId) || null,
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

type TypeFilter = '' | 'business' | 'person';
type RangeFilter = '' | string;
type SortKey = 'name' | 'orders' | 'spent' | 'last' | 'newest';

interface Range {
  value: string;
  label: string;
  min: number;
  /** Excluido; sin max = sin limite */
  max?: number;
}

const DAY = 24 * 60 * 60 * 1000;

const ORDERS_RANGES: Range[] = [
  { value: 'none', label: 'Sin pedidos', min: 0, max: 1 },
  { value: '1-4', label: '1 a 4 pedidos', min: 1, max: 5 },
  { value: '5-9', label: '5 a 9 pedidos', min: 5, max: 10 },
  { value: '10+', label: '10 pedidos o más', min: 10 },
];

const SPENT_RANGES: Range[] = [
  { value: '0-20', label: 'Menos de 20 €', min: 0, max: 20 },
  { value: '20-50', label: '20 € a 50 €', min: 20, max: 50 },
  { value: '50-150', label: '50 € a 150 €', min: 50, max: 150 },
  { value: '150+', label: '150 € o más', min: 150 },
];

function inRange(value: number, range: Range): boolean {
  return value >= range.min && (range.max === undefined || value < range.max);
}

const SORTERS: Record<SortKey, (a: Customer, b: Customer) => number> = {
  name: (a, b) => a.name.localeCompare(b.name, 'es'),
  orders: (a, b) => b.orderCount - a.orderCount || b.totalSpent - a.totalSpent,
  spent: (a, b) => b.totalSpent - a.totalSpent,
  last: (a, b) => (b.lastOrderDate ?? '').localeCompare(a.lastOrderDate ?? ''),
  newest: (a, b) => b.createdAt.localeCompare(a.createdAt),
};
