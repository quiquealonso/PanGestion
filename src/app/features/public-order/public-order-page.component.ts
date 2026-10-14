import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { Category } from '../../core/models/category.model';
import { Order } from '../../core/models/order.model';
import { PublicProduct } from '../../core/models/product.model';
import { Store, TimeSlot } from '../../core/models/store.model';
import { PublicApiService } from '../../core/services/public-api.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { addDays, toIsoDate } from '../../core/utils/dates';

/** Pantalla publica para que los clientes hagan su pedido sin llamar. */
@Component({
  imports: [CurrencyPipe, DatePipe, FormsModule],
  selector: 'app-public-order-page',
  styleUrl: './public-order-page.component.scss',
  templateUrl: './public-order-page.component.html',
})
export class PublicOrderPageComponent implements OnInit {
  private readonly api = inject(PublicApiService);

  readonly stores = signal<Store[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly products = signal<PublicProduct[]>([]);
  readonly slots = signal<TimeSlot[]>([]);
  /** Proximos 7 dias para elegir con un clic. */
  readonly days = Array.from({ length: 7 }, (_, i) => addDays(i));

  readonly storeId = signal<number | null>(null);
  readonly date = signal(toIsoDate());
  readonly time = signal<string | null>(null);
  readonly categoryFilter = signal<number | null>(null);
  readonly quantities = signal<Record<number, number>>({});

  customer = { name: '', email: '', phone: '' };
  notes = '';

  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  readonly confirmed = signal<Order | null>(null);

  readonly visibleProducts = computed(() =>
    this.products().filter((p) => !this.categoryFilter() || p.categoryId === this.categoryFilter()),
  );
  readonly lines = computed(() =>
    this.products()
      .filter((p) => (this.quantities()[p.id] ?? 0) > 0)
      .map((product) => ({ product, quantity: this.quantities()[product.id] })),
  );
  readonly total = computed(() => this.lines().reduce((sum, l) => sum + l.product.price * l.quantity, 0));
  readonly store = computed(() => this.stores().find((s) => s.id === this.storeId()) ?? null);

  ngOnInit(): void {
    forkJoin({ stores: this.api.stores(), categories: this.api.categories(), products: this.api.products() }).subscribe({
      next: ({ stores, categories, products }) => {
        this.stores.set(stores);
        this.categories.set(categories.filter((c) => products.some((p) => p.categoryId === c.id)));
        this.products.set(products);
        if (stores.length > 0) this.selectStore(stores[0].id);
      },
      error: (err) => this.error.set(apiErrorMessage(err)),
    });
  }

  selectStore(id: number): void {
    this.storeId.set(id);
    this.loadSlots();
  }

  selectDate(date: string): void {
    this.date.set(date);
    this.loadSlots();
  }

  private loadSlots(): void {
    this.time.set(null);
    const storeId = this.storeId();
    if (storeId) {
      this.api.slots(storeId, this.date()).subscribe((slots) => this.slots.set(slots));
    }
  }

  change(productId: number, delta: number): void {
    this.quantities.update((q) => ({ ...q, [productId]: Math.max(0, (q[productId] ?? 0) + delta) }));
  }

  canConfirm(): boolean {
    return !!this.time() && this.lines().length > 0 && !!this.customer.name.trim() && /\S+@\S+\.\S+/.test(this.customer.email);
  }

  confirm(): void {
    if (!this.canConfirm() || this.sending()) return;
    this.sending.set(true);
    this.error.set(null);
    this.api
      .createOrder({
        storeId: this.storeId()!,
        pickupDate: this.date(),
        pickupTime: this.time()!,
        customer: { name: this.customer.name, email: this.customer.email, phone: this.customer.phone || null, companyId: null },
        notes: this.notes || null,
        lines: this.lines().map((l) => ({ productId: l.product.id, quantity: l.quantity })),
      })
      .subscribe({
        next: (order) => {
          this.confirmed.set(order);
          this.sending.set(false);
        },
        error: (err) => {
          this.error.set(apiErrorMessage(err));
          this.sending.set(false);
          this.loadSlots();
        },
      });
  }

  newOrder(): void {
    this.confirmed.set(null);
    this.quantities.set({});
    this.notes = '';
    this.loadSlots();
  }
}
