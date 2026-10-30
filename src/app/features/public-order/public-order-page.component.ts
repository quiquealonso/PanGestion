import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, ElementRef, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { Category } from '../../core/models/category.model';
import { BUSINESS_TYPE_LABELS, Company, TimeSlot } from '../../core/models/company.model';
import { Order } from '../../core/models/order.model';
import { PublicProduct } from '../../core/models/product.model';
import { PublicApiService } from '../../core/services/public-api.service';
import { Extra } from '../../core/models/ingredient.model';
import { apiErrorMessage } from '../../core/utils/api-error';
import { addDays, toIsoDate } from '../../core/utils/dates';
import { ExtrasPickerComponent, customizationText, extrasPrice, toggleId } from '../../shared/extras-picker.component';

/** Una linea del carrito: producto, cantidad, ingredientes quitados ("sin cebolla") y extras ("con huevo"). */
interface CartLine {
  key: number;
  product: PublicProduct;
  quantity: number;
  removed: number[];
  added: number[];
}

/** Lo que se guarda en sessionStorage para no perder el pedido si se recarga la pagina. */
interface SavedProgress {
  companyId: number;
  step: number;
  cart: { productId: number; quantity: number; removed: number[]; added?: number[] }[];
  customer: { name: string; phone: string; email: string };
  notes: string;
}

const STORAGE_KEY = 'pangestion.public-order';

/**
 * Web publica para hacer un pedido, pensada para cualquier persona y para el movil:
 * un paso cada vez, con instrucciones claras y botones grandes.
 *   1. Donde pedir  2. Productos  3. Revisar  4. Tus datos  5. Dia y hora (y confirmar)
 */
import { AssetUrlPipe } from '../../shared/asset-url.pipe';

@Component({
  imports: [CurrencyPipe, DatePipe, FormsModule, ExtrasPickerComponent, AssetUrlPipe],
  selector: 'app-public-order-page',
  styleUrl: './public-order-page.component.scss',
  templateUrl: './public-order-page.component.html',
})
export class PublicOrderPageComponent implements OnInit {
  private readonly api = inject(PublicApiService);
  private readonly stepTitle = viewChild<ElementRef<HTMLElement>>('stepTitle');
  private readonly hoursTitle = viewChild<ElementRef<HTMLElement>>('hoursTitle');

  readonly steps = [
    { number: 1, title: '¿Dónde quieres pedir?', help: 'Toca el establecimiento al que quieres hacer el pedido.' },
    { number: 2, title: 'Elige tus productos', help: 'Toca un producto para ver lo que lleva y añadirlo. Arriba puedes cambiar de categoría.' },
    { number: 3, title: 'Revisa tu pedido', help: 'Comprueba las cantidades. Con el lápiz quitas ingredientes y con la papelera eliminas el producto.' },
    { number: 4, title: 'Tus datos', help: 'Los usamos solo para avisarte si hay algún problema con tu pedido.' },
    { number: 5, title: '¿Cuándo lo recoges?', help: 'Elige el día y la hora a la que pasarás a recogerlo. Después, confirma el pedido.' },
  ];
  readonly typeLabels = BUSINESS_TYPE_LABELS;

  readonly step = signal(1);
  readonly companies = signal<Company[]>([]);
  readonly companyId = signal<number | null>(null);
  readonly categories = signal<Category[]>([]);
  readonly products = signal<PublicProduct[]>([]);
  readonly activeCategory = signal<number | null>(null);
  readonly cart = signal<CartLine[]>([]);
  private nextKey = 1;

  /** Panel del producto abierto en el paso 2: ingredientes quitados y cantidad a añadir. */
  readonly detail = signal<{ product: PublicProduct; removed: number[]; added: number[]; quantity: number } | null>(null);
  /** Extras del establecimiento (ingredientes con precio de extra) */
  readonly extras = signal<Extra[]>([]);
  /** Texto del aviso "Añadido: 2 × Vegetal sin cebolla" que sale unos segundos en la barra inferior. */
  readonly justAdded = signal<string | null>(null);
  private justAddedTimer?: ReturnType<typeof setTimeout>;

  /**
   * Panel de "Quitar ingredientes" abierto sobre una linea del carrito.
   * units = a cuantas unidades de esa linea se aplica (si son menos que la linea, se separan como variante aparte).
   */
  readonly customizing = signal<{ key: number; product: PublicProduct; removed: number[]; added: number[]; units: number; max: number } | null>(null);

  readonly customer = signal({ name: '', phone: '', email: '' });
  /** Recoger en el local o a domicilio (solo si el establecimiento hace envios) */
  readonly orderType = signal<'RECOGER' | 'DOMICILIO'>('RECOGER');
  readonly address = signal('');
  readonly isDelivery = computed(() => this.orderType() === 'DOMICILIO' && !!this.company()?.delivery);
  /** Se marcan los errores del formulario solo despues de intentar continuar. */
  readonly showErrors = signal(false);
  notes = '';

  /** Proximos 7 dias. */
  readonly days = Array.from({ length: 7 }, (_, i) => addDays(i));
  readonly today = toIsoDate();
  readonly tomorrow = addDays(1);
  readonly date = signal(toIsoDate());
  readonly time = signal<string | null>(null);
  readonly slots = signal<TimeSlot[]>([]);
  readonly loadingSlots = signal(false);

  readonly loading = signal(true);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  readonly confirmed = signal<Order | null>(null);
  /** Panel final "Revisa y confirma" abierto. */
  readonly reviewing = signal(false);

  // ----- Calculados -----

  readonly company = computed(() => this.companies().find((c) => c.id === this.companyId()) ?? null);
  readonly current = computed(() => this.steps[this.step() - 1]);
  readonly visibleProducts = computed(() => {
    const category = this.activeCategory() ?? this.categories()[0]?.id;
    return this.products().filter((p) => p.categoryId === category);
  });
  readonly itemCount = computed(() => this.cart().reduce((sum, l) => sum + l.quantity, 0));
  readonly total = computed(() => this.cart().reduce((sum, l) => sum + this.unitPrice(l) * l.quantity, 0));

  /** Franjas que aun se pueden elegir, agrupadas por parte del dia (las pasadas no se ensenan). */
  readonly slotGroups = computed(() => {
    const now = new Date();
    const nowMinutes = now.getHours() * 60 + now.getMinutes();
    const visible = this.slots().filter((s) => this.date() !== this.today || minutes(s.time) > nowMinutes);
    const groups = [
      { label: 'Por la mañana', slots: visible.filter((s) => minutes(s.time) < 14 * 60) },
      { label: 'Por la tarde', slots: visible.filter((s) => minutes(s.time) >= 14 * 60 && minutes(s.time) < 20 * 60) },
      { label: 'Por la noche', slots: visible.filter((s) => minutes(s.time) >= 20 * 60) },
    ];
    return groups.filter((g) => g.slots.length > 0);
  });
  readonly hasFreeSlots = computed(() => this.slotGroups().some((g) => g.slots.some((s) => s.available)));

  /** Errores del formulario de datos, en palabras sencillas. */
  readonly customerErrors = computed(() => {
    const c = this.customer();
    return {
      name: c.name.trim().length < 2 ? 'Escribe tu nombre.' : null,
      email: !/^\S+@\S+\.\S+$/.test(c.email.trim()) ? 'Escribe un email válido, por ejemplo: nombre@gmail.com' : null,
      phone: c.phone.trim() && !/^[0-9 +]{9,15}$/.test(c.phone.trim()) ? 'Escribe un teléfono de 9 cifras, por ejemplo: 612 345 678' : null,
      // A domicilio: el repartidor necesita la direccion y un telefono para llamar al llegar
      address: this.isDelivery() && this.address().trim().length < 5 ? 'Escribe la dirección: calle, número y piso.' : null,
      deliveryPhone: this.isDelivery() && !c.phone.trim() ? 'Para llevártelo necesitamos un teléfono.' : null,
    };
  });

  /** Por que no se puede continuar en el paso actual (null = se puede). */
  readonly blockedReason = computed(() => {
    switch (this.step()) {
      case 1:
        return this.companyId() ? null : 'Elige un establecimiento para continuar.';
      case 2:
      case 3:
        return this.itemCount() > 0 ? null : 'Añade al menos un producto para continuar.';
      case 4: {
        const e = this.customerErrors();
        return e.name || e.email || e.phone || e.address || e.deliveryPhone ? 'Revisa tus datos: hay algún campo sin completar.' : null;
      }
      case 5:
        return this.time() ? null : `Elige una hora de ${this.isDelivery() ? 'entrega' : 'recogida'} para poder confirmar.`;
      default:
        return null;
    }
  });

  constructor() {
    // Guarda el progreso para no perderlo si se recarga la pagina
    effect(() => {
      const companyId = this.companyId();
      if (!companyId || this.confirmed()) return;
      const progress: SavedProgress = {
        companyId,
        step: this.step(),
        cart: this.cart().map((l) => ({ productId: l.product.id, quantity: l.quantity, removed: l.removed, added: l.added })),
        customer: this.customer(),
        notes: this.notes,
      };
      try {
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(progress));
      } catch {
        // Sin almacenamiento (modo privado...): simplemente no se guarda
      }
    });
  }

  ngOnInit(): void {
    const saved = this.readProgress();
    this.api.companies().subscribe({
      next: (companies) => {
        this.companies.set(companies);
        this.loading.set(false);
        const restore = saved && companies.some((c) => c.id === saved.companyId) ? saved : null;
        if (restore) {
          this.selectCompany(restore.companyId, restore);
        } else if (companies.length === 1) {
          // Si solo hay un establecimiento no hace falta elegir: se salta el paso 1
          this.selectCompany(companies[0].id);
          this.step.set(2);
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err));
      },
    });
  }

  // ----- Paso 1: establecimiento -----

  /** Al elegir establecimiento se carga su carta. Si se cambia de establecimiento, el carrito se vacia. */
  selectCompany(id: number, restore?: SavedProgress): void {
    if (this.companyId() !== id) {
      this.cart.set([]);
      this.activeCategory.set(null);
    }
    this.companyId.set(id);
    forkJoin({ categories: this.api.categories(id), products: this.api.products(id), extras: this.api.extras(id) }).subscribe({
      next: ({ categories, products, extras }) => {
        this.categories.set(categories.filter((c) => products.some((p) => p.categoryId === c.id)));
        this.products.set(products);
        this.extras.set(extras);
        if (restore) this.applyRestore(restore, products);
      },
      error: (err) => this.error.set(apiErrorMessage(err)),
    });
    this.loadSlots();
    if (!restore && this.step() === 1) this.next();
  }

  // ----- Paso 2 y 3: carrito -----

  quantityOf(productId: number): number {
    return this.cart()
      .filter((l) => l.product.id === productId)
      .reduce((sum, l) => sum + l.quantity, 0);
  }

  // Panel del producto (paso 2)

  openDetail(product: PublicProduct): void {
    this.detail.set({ product, removed: [], added: [], quantity: 1 });
  }

  toggleDetailIngredient(id: number): void {
    this.detail.update((d) =>
      d ? { ...d, removed: d.removed.includes(id) ? d.removed.filter((x) => x !== id) : [...d.removed, id].sort((a, b) => a - b) } : d,
    );
  }

  toggleDetailExtra(id: number): void {
    this.detail.update((d) => (d ? { ...d, added: toggleId(d.added, id) } : d));
  }

  /** Precio de una unidad: el producto mas sus extras. */
  unitPrice(line: { product: PublicProduct; added: number[] }): number {
    return line.product.price + extrasPrice(this.extras(), line.added);
  }

  changeDetailQuantity(delta: number): void {
    this.detail.update((d) => (d ? { ...d, quantity: Math.max(1, d.quantity + delta) } : d));
  }

  /**
   * Añade el producto al pedido. Si ya habia uno igual (mismos ingredientes quitados y extras) suma la cantidad;
   * si no, queda como una linea aparte: "Vegetal" y "Vegetal sin cebolla" son dos lineas distintas.
   */
  addFromDetail(): void {
    const d = this.detail();
    if (!d) return;
    const same = this.cart().find((l) => l.product.id === d.product.id && sameCustomization(l, d));
    if (same) this.changeLine(same, d.quantity);
    else this.cart.update((lines) => [...lines, { key: this.nextKey++, product: d.product, quantity: d.quantity, removed: d.removed, added: d.added }]);

    const removed = this.removedLabel(d);
    this.justAdded.set(`${d.quantity} × ${d.product.name}${removed ? ' ' + removed : ''}`);
    clearTimeout(this.justAddedTimer);
    this.justAddedTimer = setTimeout(() => this.justAdded.set(null), 2500);
    this.detail.set(null);
  }

  changeLine(line: CartLine, delta: number): void {
    this.cart.update((lines) =>
      lines.map((l) => (l.key === line.key ? { ...l, quantity: Math.max(0, l.quantity + delta) } : l)).filter((l) => l.quantity > 0),
    );
  }

  removeLine(line: CartLine): void {
    this.cart.update((lines) => lines.filter((l) => l.key !== line.key));
  }

  /** "con huevo, sin cebolla, sin queso": lo que se anade y lo que se quita */
  removedLabel(line: { product: PublicProduct; removed: number[]; added: number[] }): string {
    return customizationText(
      line.product.ingredients.filter((i) => line.removed.includes(i.id)).map((i) => i.name),
      this.extras().filter((e) => line.added.includes(e.id)).map((e) => e.name),
    );
  }

  /** Abre el panel. Si la linea tiene varias unidades, por defecto el cambio se aplica a una sola. */
  openCustomize(line: CartLine): void {
    this.customizing.set({ key: line.key, product: line.product, removed: [...line.removed], added: [...line.added], units: 1, max: line.quantity });
  }

  changeUnits(delta: number): void {
    this.customizing.update((c) => (c ? { ...c, units: Math.min(c.max, Math.max(1, c.units + delta)) } : c));
  }

  toggleIngredient(id: number): void {
    this.customizing.update((c) =>
      c ? { ...c, removed: c.removed.includes(id) ? c.removed.filter((x) => x !== id) : [...c.removed, id].sort((a, b) => a - b) } : c,
    );
  }

  toggleExtra(id: number): void {
    this.customizing.update((c) => (c ? { ...c, added: toggleId(c.added, id) } : c));
  }

  /**
   * Guarda los ingredientes quitados y los extras. Si se aplica solo a algunas unidades, esas se separan en una linea nueva
   * (como si fuera otro producto). Las lineas que quedan iguales se juntan: dos "sin cebolla" suman en una.
   */
  applyCustomize(): void {
    const c = this.customizing();
    if (!c) return;
    const lines: CartLine[] = [];
    for (const l of this.cart()) {
      if (l.key !== c.key) {
        lines.push(l);
      } else if (c.units >= l.quantity) {
        lines.push({ ...l, removed: c.removed, added: c.added });
      } else {
        lines.push({ ...l, quantity: l.quantity - c.units });
        lines.push({ key: this.nextKey++, product: l.product, quantity: c.units, removed: c.removed, added: c.added });
      }
    }
    const merged: CartLine[] = [];
    for (const line of lines) {
      const same = merged.find((m) => m.product.id === line.product.id && sameCustomization(m, line));
      if (same) same.quantity += line.quantity;
      else merged.push({ ...line });
    }
    this.cart.set(merged);
    this.customizing.set(null);
  }

  // ----- Paso 4: datos -----

  updateCustomer(field: 'name' | 'phone' | 'email', value: string): void {
    this.customer.update((c) => ({ ...c, [field]: value }));
  }

  // ----- Paso 5: dia y hora -----

  /** "Hoy", "Mañana" o el nombre del dia. */
  dayName(day: string): string {
    if (day === this.today) return 'Hoy';
    if (day === this.tomorrow) return 'Mañana';
    return new Date(day + 'T00:00').toLocaleDateString('es-ES', { weekday: 'long' });
  }

  /** Al elegir el dia se baja solo hasta las horas, que es lo siguiente que hay que hacer. */
  selectDate(day: string): void {
    this.date.set(day);
    this.loadSlots(true);
  }

  /** scrollToHours: cuando lleguen las horas, bajar hasta ellas (antes la pagina aun es corta y no puede bajar). */
  private loadSlots(scrollToHours = false): void {
    this.time.set(null);
    const companyId = this.companyId();
    if (!companyId) return;
    this.loadingSlots.set(true);
    this.api.slots(companyId, this.date()).subscribe({
      next: (slots) => {
        this.slots.set(slots);
        this.loadingSlots.set(false);
        if (scrollToHours) {
          setTimeout(() => this.hoursTitle()?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' }), 0);
        }
      },
      error: () => this.loadingSlots.set(false),
    });
  }

  // ----- Navegacion entre pasos -----

  next(): void {
    if (this.step() === 4) this.showErrors.set(true);
    if (this.blockedReason()) return;
    this.goTo(this.step() + 1);
  }

  back(): void {
    // Con un solo establecimiento no se vuelve al paso 1
    const min = this.companies().length === 1 ? 2 : 1;
    if (this.step() > min) this.goTo(this.step() - 1);
  }

  /** Solo se puede ir a un paso ya visitado o al siguiente si el actual esta completo. */
  goTo(step: number): void {
    if (step > this.step() && this.blockedReason()) return;
    this.step.set(step);
    this.error.set(null);
    // Arriba del todo y el foco en el titulo del paso (para lectores de pantalla)
    window.scrollTo({ top: 0, behavior: 'smooth' });
    setTimeout(() => this.stepTitle()?.nativeElement.focus(), 0);
  }

  /** Paso 5: abre el resumen final (solo si ya hay hora elegida). */
  openReview(): void {
    if (this.blockedReason()) return;
    this.error.set(null);
    this.reviewing.set(true);
  }

  /** "Cambiar" en el resumen: cierra el panel y vuelve a ese paso. */
  editFromReview(step: number): void {
    this.reviewing.set(false);
    this.goTo(step);
  }

  confirm(): void {
    if (this.blockedReason() || this.sending()) return;
    const c = this.customer();
    this.sending.set(true);
    this.error.set(null);
    this.api
      .createOrder({
        companyId: this.companyId()!,
        pickupDate: this.date(),
        pickupTime: this.time()!,
        customer: { name: c.name.trim(), email: c.email.trim(), phone: c.phone.trim() || null, businessName: null, taxId: null },
        notes: this.notes.trim() || null,
        type: this.isDelivery() ? 'DOMICILIO' : 'RECOGER',
        deliveryAddress: this.isDelivery() ? this.address().trim() : null,
        lines: this.cart().map((l) => ({
          productId: l.product.id,
          quantity: l.quantity,
          removedIngredientIds: l.removed,
          addedIngredientIds: l.added,
        })),
      })
      .subscribe({
        next: (order) => {
          this.confirmed.set(order);
          this.sending.set(false);
          this.reviewing.set(false);
          this.clearProgress();
          window.scrollTo({ top: 0 });
        },
        error: (err) => {
          // Lo mas habitual: la hora se ha llenado mientras tanto. Se recargan las horas.
          this.error.set(apiErrorMessage(err));
          this.sending.set(false);
          this.loadSlots();
        },
      });
  }

  /** Empezar otro pedido en el mismo establecimiento (se mantienen los datos del cliente). */
  newOrder(): void {
    this.confirmed.set(null);
    this.cart.set([]);
    this.notes = '';
    this.showErrors.set(false);
    this.loadSlots();
    this.goTo(2);
  }

  // ----- Guardar / recuperar el progreso -----

  private readProgress(): SavedProgress | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as SavedProgress) : null;
    } catch {
      return null;
    }
  }

  private applyRestore(saved: SavedProgress, products: PublicProduct[]): void {
    const lines: CartLine[] = [];
    for (const item of saved.cart) {
      const product = products.find((p) => p.id === item.productId);
      if (product) lines.push({ key: this.nextKey++, product, quantity: item.quantity, removed: item.removed, added: item.added ?? [] });
    }
    this.cart.set(lines);
    this.customer.set(saved.customer);
    this.notes = saved.notes;
    // El dia y la hora no se recuperan (pueden haber pasado): como mucho se vuelve al paso de datos
    this.step.set(lines.length ? Math.min(saved.step, 4) : 2);
  }

  private clearProgress(): void {
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // nada
    }
  }
}

function minutes(time: string): number {
  const [h, m] = time.split(':').map(Number);
  return h * 60 + m;
}

/** Mismos ingredientes quitados y mismos extras: dos lineas del mismo producto se pueden juntar. */
function sameCustomization(a: { removed: number[]; added: number[] }, b: { removed: number[]; added: number[] }): boolean {
  return a.removed.join() === b.removed.join() && a.added.join() === b.added.join();
}
