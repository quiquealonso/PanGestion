import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Customer } from '../../../core/models/customer.model';
import { Product } from '../../../core/models/product.model';
import { Store, TimeSlot } from '../../../core/models/store.model';
import { CustomersService } from '../../../core/services/customers.service';
import { NotificationService } from '../../../core/services/notification.service';
import { OrdersService } from '../../../core/services/orders.service';
import { ProductsService } from '../../../core/services/products.service';
import { StoresService } from '../../../core/services/stores.service';
import { apiErrorMessage } from '../../../core/utils/api-error';
import { toIsoDate } from '../../../core/utils/dates';

/** Alta y edicion de pedidos desde el panel, en 4 pasos. */
@Component({
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink],
  selector: 'app-create-order-page',
  styleUrl: './create-order-page.component.scss',
  templateUrl: './create-order-page.component.html',
})
export class CreateOrderPageComponent implements OnInit {
  private readonly ordersService = inject(OrdersService);
  private readonly storesService = inject(StoresService);
  private readonly productsService = inject(ProductsService);
  private readonly customersService = inject(CustomersService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);

  /** /orders/:id/edit -> modo edicion. */
  readonly id = input<string>();
  readonly isEdit = computed(() => !!this.id());

  readonly step = signal(1);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly today = toIsoDate();

  // Datos de apoyo
  readonly stores = signal<Store[]>([]);
  readonly products = signal<Product[]>([]);
  readonly customers = signal<Customer[]>([]);
  readonly slots = signal<TimeSlot[]>([]);

  // Paso 1: donde y cuando
  readonly storeId = signal<number | null>(null);
  readonly date = signal(toIsoDate());
  readonly time = signal<string | null>(null);
  /** Franja original al editar: se puede mantener aunque este completa. */
  private originalSlot: { storeId: number; date: string; time: string } | null = null;

  // Paso 2: productos
  readonly quantities = signal<Record<number, number>>({});

  // Paso 3: cliente
  readonly customerId = signal<number | null>(null);
  readonly showNewCustomer = signal(false);
  customerSearch = '';
  newCustomer = { name: '', email: '', phone: '' };

  // Paso 4
  notes = '';

  /** Productos agrupados por categoria. */
  readonly categories = computed(() => {
    const groups = new Map<string, { name: string; color: string; products: Product[] }>();
    for (const product of this.products()) {
      const group = groups.get(product.categoryName) ?? { name: product.categoryName, color: product.categoryColor, products: [] };
      group.products.push(product);
      groups.set(product.categoryName, group);
    }
    return [...groups.values()];
  });

  readonly selectedLines = computed(() =>
    this.products()
      .filter((product) => (this.quantities()[product.id] ?? 0) > 0)
      .map((product) => ({ product, quantity: this.quantities()[product.id] })),
  );

  readonly total = computed(() => this.selectedLines().reduce((sum, line) => sum + line.product.price * line.quantity, 0));
  readonly selectedStore = computed(() => this.stores().find((store) => store.id === this.storeId()) ?? null);
  readonly selectedCustomer = computed(() => this.customers().find((customer) => customer.id === this.customerId()) ?? null);

  ngOnInit(): void {
    forkJoin({
      stores: this.storesService.list(),
      products: this.productsService.search('', null, null),
      customers: this.customersService.search(''),
    }).subscribe({
      next: ({ stores, products, customers }) => {
        this.stores.set(stores.filter((store) => store.active));
        this.products.set(products);
        this.customers.set(customers);
        if (this.isEdit()) {
          this.loadOrder(Number(this.id()));
        } else if (this.stores().length > 0) {
          this.selectStore(this.stores()[0].id);
        }
      },
      error: (err) => this.error.set(apiErrorMessage(err)),
    });
  }

  private loadOrder(id: number): void {
    this.ordersService.get(id).subscribe({
      next: (order) => {
        this.originalSlot = { storeId: order.storeId, date: order.pickupDate, time: order.pickupTime };
        this.storeId.set(order.storeId);
        this.date.set(order.pickupDate);
        this.time.set(order.pickupTime);
        this.customerId.set(order.customerId);
        this.notes = order.notes ?? '';
        const quantities: Record<number, number> = {};
        order.lines.forEach((line) => (quantities[line.productId] = line.quantity));
        this.quantities.set(quantities);
        this.loadSlots();
      },
      error: (err) => this.error.set(apiErrorMessage(err)),
    });
  }

  /** Productos visibles: los activos y, al editar, los que ya estaban en el pedido. */
  isVisible(product: Product): boolean {
    return product.active || (this.quantities()[product.id] ?? 0) > 0;
  }

  selectStore(storeId: number): void {
    this.storeId.set(storeId);
    this.time.set(null);
    this.loadSlots();
  }

  selectDate(date: string): void {
    this.date.set(date);
    this.time.set(null);
    this.loadSlots();
  }

  loadSlots(): void {
    const storeId = this.storeId();
    if (!storeId || !this.date()) {
      this.slots.set([]);
      return;
    }
    this.storesService.slots(storeId, this.date()).subscribe((slots) => this.slots.set(slots));
  }

  isSlotSelectable(slot: TimeSlot): boolean {
    const original = this.originalSlot;
    const isOriginal = !!original && original.storeId === this.storeId() && original.date === this.date() && original.time === slot.time;
    return slot.available || isOriginal;
  }

  changeQuantity(productId: number, delta: number): void {
    this.quantities.update((current) => ({ ...current, [productId]: Math.max(0, (current[productId] ?? 0) + delta) }));
  }

  setQuantity(productId: number, value: number): void {
    this.quantities.update((current) => ({ ...current, [productId]: Math.max(0, Math.floor(value || 0)) }));
  }

  searchCustomers(): void {
    this.customersService.search(this.customerSearch).subscribe((customers) => this.customers.set(customers));
  }

  createCustomer(): void {
    const { name, email, phone } = this.newCustomer;
    if (!name || !email) {
      this.notify.error('Nombre y email son obligatorios');
      return;
    }
    this.customersService.create({ name, email, phone: phone || null, companyId: null }).subscribe({
      next: (customer) => {
        this.customers.update((customers) => [customer, ...customers]);
        this.customerId.set(customer.id);
        this.showNewCustomer.set(false);
        this.newCustomer = { name: '', email: '', phone: '' };
        this.notify.success(`Cliente ${customer.name} creado`);
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  /** Cada paso exige haber completado los anteriores. */
  canGoTo(step: number): boolean {
    if (step >= 2 && !(this.storeId() && this.date() && this.time())) return false;
    if (step >= 3 && this.selectedLines().length === 0) return false;
    if (step >= 4 && !this.customerId()) return false;
    return true;
  }

  goTo(step: number): void {
    if (this.canGoTo(step)) {
      this.step.set(step);
    }
  }

  save(): void {
    if (!this.canGoTo(4)) {
      return;
    }
    const body = {
      customerId: this.customerId()!,
      storeId: this.storeId()!,
      pickupDate: this.date(),
      pickupTime: this.time()!,
      notes: this.notes || null,
      lines: this.selectedLines().map((line) => ({ productId: line.product.id, quantity: line.quantity })),
    };
    this.saving.set(true);
    this.error.set(null);
    const request = this.isEdit() ? this.ordersService.update(Number(this.id()), body) : this.ordersService.create(body);
    request.subscribe({
      next: (order) => {
        this.notify.success(this.isEdit() ? `Pedido #${order.id} actualizado` : `Pedido #${order.id} creado`);
        this.router.navigate(['/orders', order.id]);
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err));
        this.saving.set(false);
      },
    });
  }
}
