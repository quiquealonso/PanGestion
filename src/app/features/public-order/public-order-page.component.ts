import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { Category } from '../../core/models/category.model';
import { Order } from '../../core/models/order.model';
import { PublicProduct } from '../../core/models/product.model';
import { BUSINESS_TYPE_LABELS, Company, TimeSlot } from '../../core/models/company.model';
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

  readonly typeLabels = BUSINESS_TYPE_LABELS;
  readonly companies = signal<Company[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly products = signal<PublicProduct[]>([]);
  readonly slots = signal<TimeSlot[]>([]);
  /** Proximos 7 dias para elegir con un clic. */
  readonly days = Array.from({ length: 7 }, (_, i) => addDays(i));

  /** Panaderia elegida por el cliente. */
  readonly companyId = signal<number | null>(null);
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
  readonly company = computed(() => this.companies().find((c) => c.id === this.companyId()) ?? null);

  ngOnInit(): void {
    this.api.companies().subscribe({
      next: (companies) => {
        this.companies.set(companies);
        if (companies.length > 0) this.selectCompany(companies[0].id);
      },
      error: (err) => this.error.set(apiErrorMessage(err)),
    });
  }

  /** Al cambiar de panaderia se carga su catalogo y se vacia el carrito. */
  selectCompany(id: number): void {
    if (this.companyId() === id) return;
    this.companyId.set(id);
    this.quantities.set({});
    this.categoryFilter.set(null);
    forkJoin({ categories: this.api.categories(id), products: this.api.products(id) }).subscribe({
      next: ({ categories, products }) => {
        this.categories.set(categories.filter((c) => products.some((p) => p.categoryId === c.id)));
        this.products.set(products);
      },
      error: (err) => this.error.set(apiErrorMessage(err)),
    });
    this.loadSlots();
  }

  selectDate(date: string): void {
    this.date.set(date);
    this.loadSlots();
  }

  private loadSlots(): void {
    this.time.set(null);
    const companyId = this.companyId();
    if (companyId) {
      this.api.slots(companyId, this.date()).subscribe((slots) => this.slots.set(slots));
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
        companyId: this.companyId()!,
        pickupDate: this.date(),
        pickupTime: this.time()!,
        customer: { name: this.customer.name, email: this.customer.email, phone: this.customer.phone || null, businessName: null, taxId: null },
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
