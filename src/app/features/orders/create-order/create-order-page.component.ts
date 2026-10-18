import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Customer } from '../../../core/models/customer.model';
import { Product } from '../../../core/models/product.model';
import { TimeSlot } from '../../../core/models/company.model';
import { CustomersService } from '../../../core/services/customers.service';
import { MyCompanyService } from '../../../core/services/my-company.service';
import { NotificationService } from '../../../core/services/notification.service';
import { OrdersService } from '../../../core/services/orders.service';
import { ProductsService } from '../../../core/services/products.service';
import { apiErrorMessage } from '../../../core/utils/api-error';
import { toIsoDate } from '../../../core/utils/dates';
import { ModalComponent } from '../../../shared/modal/modal.component';

/**
 * Alta y edicion de pedidos desde el panel, con el mismo flujo que la caja de una tienda:
 * 1) productos (con carrito flotante) -> 2) resumen del carrito -> 3) cliente -> 4) dia y franja de recogida.
 */
/** Linea del carrito: un producto con su cantidad y, si se personaliza, los ingredientes que se le quitan. */
interface CartLine {
  key: number;
  product: Product;
  quantity: number;
  /** Ids de los ingredientes quitados (ordenados). Vacio = producto tal cual. */
  removed: number[];
}

/** Dialogo de personalizar: una linea existente (key) o una variante nueva (key = null). */
interface CustomizeState {
  key: number | null;
  product: Product;
  quantity: number;
  removed: number[];
}

@Component({
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, ModalComponent],
  selector: 'app-create-order-page',
  styleUrl: './create-order-page.component.scss',
  templateUrl: './create-order-page.component.html',
})
export class CreateOrderPageComponent implements OnInit {
  private readonly ordersService = inject(OrdersService);
  private readonly myCompanyService = inject(MyCompanyService);
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
  readonly products = signal<Product[]>([]);
  readonly customers = signal<Customer[]>([]);
  readonly slots = signal<TimeSlot[]>([]);

  // Paso 4: cuando se recoge
  readonly date = signal(toIsoDate());
  readonly time = signal<string | null>(null);
  /** Franja original al editar: se puede mantener aunque este completa. */
  private originalSlot: { date: string; time: string } | null = null;

  // Paso 1: carrito
  readonly cart = signal<CartLine[]>([]);
  private nextKey = 1;
  /** Dialogo de personalizar abierto (null = cerrado). */
  readonly customizing = signal<CustomizeState | null>(null);
  /** Pestana de categoria activa en el paso de productos. */
  readonly activeCategory = signal<string | null>(null);

  // Paso 3: cliente
  readonly customerId = signal<number | null>(null);
  readonly showNewCustomer = signal(false);
  customerSearch = '';
  newCustomer = { name: '', email: '', phone: '' };

  // Paso 2: notas del pedido
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

  readonly selectedLines = computed(() => this.cart().filter((line) => line.quantity > 0));

  readonly total = computed(() => this.selectedLines().reduce((sum, line) => sum + line.product.price * line.quantity, 0));
  /** Unidades en el carrito. */
  readonly itemCount = computed(() => this.selectedLines().reduce((sum, line) => sum + line.quantity, 0));
  /** Productos de la pestana activa. */
  readonly visibleProducts = computed(() => {
    const category = this.categories().find((c) => c.name === this.activeCategory()) ?? this.categories()[0];
    return (category?.products ?? []).filter((product) => this.isVisible(product));
  });
  readonly selectedCustomer = computed(() => this.customers().find((customer) => customer.id === this.customerId()) ?? null);

  ngOnInit(): void {
    forkJoin({
      products: this.productsService.search('', null, null),
      customers: this.customersService.search(''),
    }).subscribe({
      next: ({ products, customers }) => {
        this.products.set(products);
        this.customers.set(customers);
        this.activeCategory.set(this.categories()[0]?.name ?? null);
        if (this.isEdit()) {
          this.loadOrder(Number(this.id()));
        } else {
          this.loadSlots();
        }
      },
      error: (err) => this.error.set(apiErrorMessage(err)),
    });
  }

  private loadOrder(id: number): void {
    this.ordersService.get(id).subscribe({
      next: (order) => {
        this.originalSlot = { date: order.pickupDate, time: order.pickupTime };
        this.date.set(order.pickupDate);
        this.time.set(order.pickupTime);
        this.customerId.set(order.customerId);
        this.notes = order.notes ?? '';
        // Los ingredientes quitados llegan por nombre: se buscan sus ids en el producto
        const lines: CartLine[] = [];
        for (const line of order.lines) {
          const product = this.products().find((p) => p.id === line.productId);
          if (!product) continue;
          const removed = product.ingredients.filter((i) => line.removedIngredients.includes(i.name)).map((i) => i.id);
          lines.push({ key: this.nextKey++, product, quantity: line.quantity, removed: removed.sort((a, b) => a - b) });
        }
        this.cart.set(lines);
        this.loadSlots();
        this.step.set(2); // al editar se empieza viendo el resumen
      },
      error: (err) => this.error.set(apiErrorMessage(err)),
    });
  }

  /** Productos visibles: los activos y, al editar, los que ya estaban en el pedido. */
  isVisible(product: Product): boolean {
    return product.active || this.quantityOf(product.id) > 0;
  }

  selectDate(date: string): void {
    this.date.set(date);
    this.time.set(null);
    this.loadSlots();
  }

  loadSlots(): void {
    if (!this.date()) {
      this.slots.set([]);
      return;
    }
    this.myCompanyService.slots(this.date()).subscribe((slots) => this.slots.set(slots));
  }

  isSlotSelectable(slot: TimeSlot): boolean {
    const original = this.originalSlot;
    const isOriginal = !!original && original.date === this.date() && original.time === slot.time;
    return slot.available || isOriginal;
  }

  /**
   * Botones + / - de la tarjeta del producto: suman o restan a la linea "tal cual" (sin personalizar).
   * Si al restar no hay linea sin personalizar, se resta de la ultima linea de ese producto.
   */
  changeQuantity(productId: number, delta: number): void {
    const product = this.products().find((p) => p.id === productId);
    if (!product) return;
    const lines = this.cart();
    const base = lines.find((l) => l.product.id === productId && l.removed.length === 0);
    if (delta > 0) {
      if (base) this.changeLine(base, delta);
      else this.cart.set([...lines, { key: this.nextKey++, product, quantity: delta, removed: [] }]);
      return;
    }
    const target = base ?? [...lines].reverse().find((l) => l.product.id === productId);
    if (target) this.changeLine(target, delta);
  }

  /** Botones + / - de una linea del resumen. Si llega a 0, la linea desaparece. */
  changeLine(line: CartLine, delta: number): void {
    this.cart.update((lines) =>
      lines
        .map((l) => (l.key === line.key ? { ...l, quantity: Math.max(0, l.quantity + delta) } : l))
        .filter((l) => l.quantity > 0),
    );
  }

  removeLine(line: CartLine): void {
    this.cart.update((lines) => lines.filter((l) => l.key !== line.key));
  }

  /** Unidades de un producto en el carrito, sumando todas sus variantes. */
  quantityOf(productId: number): number {
    return this.cart()
      .filter((l) => l.product.id === productId)
      .reduce((sum, l) => sum + l.quantity, 0);
  }

  /** "sin cebolla, sin queso" para mostrar en el carrito. */
  removedLabel(line: { product: Product; removed: number[] }): string {
    return line.product.ingredients
      .filter((i) => line.removed.includes(i.id))
      .map((i) => 'sin ' + i.name.toLowerCase())
      .join(', ');
  }

  // ----- Personalizar (quitar ingredientes) -----

  /** Personalizar una linea: afecta a todas sus unidades. */
  customize(line: CartLine): void {
    this.customizing.set({ key: line.key, product: line.product, quantity: line.quantity, removed: [...line.removed] });
  }

  /** Anadir otra unidad del mismo producto para personalizarla aparte (por ejemplo, 1 pizza sin cebolla). */
  addVariant(line: CartLine): void {
    this.customizing.set({ key: null, product: line.product, quantity: 1, removed: [...line.removed] });
  }

  toggleRemoved(ingredientId: number): void {
    this.customizing.update((state) => {
      if (!state) return state;
      const removed = state.removed.includes(ingredientId)
        ? state.removed.filter((id) => id !== ingredientId)
        : [...state.removed, ingredientId];
      return { ...state, removed: removed.sort((a, b) => a - b) };
    });
  }

  changeCustomizeQuantity(delta: number): void {
    this.customizing.update((state) => (state ? { ...state, quantity: Math.max(1, state.quantity + delta) } : state));
  }

  /** Guarda la personalizacion. Si queda igual que otra linea del mismo producto, se juntan. */
  applyCustomize(): void {
    const state = this.customizing();
    if (!state) return;
    let lines = this.cart();
    if (state.key === null) {
      lines = [...lines, { key: this.nextKey++, product: state.product, quantity: state.quantity, removed: state.removed }];
    } else {
      lines = lines.map((l) => (l.key === state.key ? { ...l, quantity: state.quantity, removed: state.removed } : l));
    }
    this.cart.set(this.mergeEqualLines(lines));
    this.customizing.set(null);
  }

  private mergeEqualLines(lines: CartLine[]): CartLine[] {
    const merged: CartLine[] = [];
    for (const line of lines) {
      const same = merged.find((m) => m.product.id === line.product.id && m.removed.join() === line.removed.join());
      if (same) same.quantity += line.quantity;
      else merged.push({ ...line });
    }
    return merged;
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
    this.customersService.create({ name, email, phone: phone || null, businessName: null, taxId: null }).subscribe({
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
    if (step >= 2 && this.selectedLines().length === 0) return false;
    if (step >= 4 && !this.customerId()) return false;
    return true;
  }

  /** Se puede confirmar cuando hay productos, cliente y franja elegida. */
  canSave(): boolean {
    return this.canGoTo(4) && !!this.date() && !!this.time();
  }


  goTo(step: number): void {
    if (this.canGoTo(step)) {
      this.step.set(step);
    }
  }

  save(): void {
    if (!this.canSave()) {
      return;
    }
    const body = {
      customerId: this.customerId()!,
      pickupDate: this.date(),
      pickupTime: this.time()!,
      notes: this.notes || null,
      lines: this.selectedLines().map((line) => ({
        productId: line.product.id,
        quantity: line.quantity,
        removedIngredientIds: line.removed,
      })),
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
