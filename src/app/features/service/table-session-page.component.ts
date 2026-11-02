import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Product } from '../../core/models/product.model';
import { FloorArea, ITEM_STATUS_LABELS, ServiceLine, TableSession } from '../../core/models/table-service.model';
import { Category } from '../../core/models/category.model';
import { CategoriesService } from '../../core/services/categories.service';
import { KitchenService } from '../../core/services/kitchen.service';
import { LiveEventsService } from '../../core/services/live-events.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NotificationService } from '../../core/services/notification.service';
import { ProductsService } from '../../core/services/products.service';
import { TableServiceService } from '../../core/services/table-service.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { Extra, LineExtra } from '../../core/models/ingredient.model';
import { IngredientsService } from '../../core/services/ingredients.service';
import { extrasPrice, toggleId } from '../../shared/extras-picker.component';
import { ModalComponent } from '../../shared/modal/modal.component';
import { PayDialogComponent } from '../../shared/pay-dialog.component';

/** Producto apuntado en la comanda que aun no se ha enviado a cocina. */
interface DraftLine {
  key: number;
  productId: number;
  name: string;
  color: string;
  price: number;
  quantity: number;
  notes: string;
  removedIds: number[];
  /** Extras anadidos (ids de ingrediente); su precio se suma al del producto */
  addedIds: number[];
}

/**
 * Un boton de la carta. Cada producto tiene el suyo (line = lo apuntado sin cambios, o null) y, justo detras,
 * uno por cada version cambiada apuntada ("Vegetal sin cebolla", "Chuleta poco hecha").
 */
interface MenuTile {
  id: string;
  product: Product;
  line: DraftLine | null;
}

const DRAFT_KEY = 'pangestion.draft.';
/** Respaldo: la mesa se refresca con los avisos en tiempo real; esto solo cubre si se corta la conexion. */
const REFRESH_MS = 60_000;
/** Cada cuanto se actualizan los minutos que llevan los productos pendientes o preparandose */
const CLOCK_MS = 15_000;

/**
 * Comanda de una mesa (pensada para el movil del camarero): se tocan productos de la carta y se envian
 * a cocina por rondas. Debajo, lo ya pedido y el total. Los productos son de toda la mesa: los comensales
 * solo cuentan para dividir la cuenta al cobrar.
 * Lo apuntado y no enviado se guarda en el navegador, para no perderlo si se recarga la pagina.
 */
@Component({
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, ModalComponent, PayDialogComponent],
  selector: 'app-table-session-page',
  templateUrl: './table-session-page.component.html',
  styleUrl: './table-session-page.component.scss',
})
export class TableSessionPageComponent implements OnInit {
  private readonly service = inject(TableServiceService);
  private readonly productsService = inject(ProductsService);
  private readonly categoriesService = inject(CategoriesService);
  private readonly ingredientsService = inject(IngredientsService);
  private readonly notify = inject(NotificationService);
  private readonly confirmService = inject(ConfirmService);
  private readonly router = inject(Router);
  private readonly kitchen = inject(KitchenService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly live = inject(LiveEventsService);
  readonly statusLabels = ITEM_STATUS_LABELS;

  /** :id de la ruta */
  readonly id = input.required({ transform: numberAttribute });

  readonly session = signal<TableSession | null>(null);
  readonly products = signal<Product[]>([]);
  readonly categories = computed(() => {
    const seen = new Map<number, { id: number; name: string; color: string }>();
    for (const p of this.products()) {
      if (!seen.has(p.categoryId)) seen.set(p.categoryId, { id: p.categoryId, name: p.categoryName, color: p.categoryColor });
    }
    return [...seen.values()];
  });
  /** Categorias del negocio: dan el orden de cocina ("Orden en cocina") para lo ya pedido */
  readonly allCategories = signal<Category[]>([]);
  readonly categoryId = signal<number | null>(null);
  /** Sin "Todo": si no se ha elegido ninguna, la primera categoria */
  readonly selectedCategory = computed(() => this.categoryId() ?? this.categories()[0]?.id ?? null);
  readonly search = signal('');
  readonly visibleProducts = computed(() => {
    const text = normalize(this.search());
    const category = this.selectedCategory();
    return this.products().filter(
      (p) => (text ? normalize(p.name).includes(text) : category === null || p.categoryId === category),
    );
  });
  readonly tiles = computed<MenuTile[]>(() => {
    const draft = this.draft();
    return this.visibleProducts().flatMap((product) => {
      const lines = draft.filter((l) => l.productId === product.id);
      const plain = lines.find(isPlain) ?? null;
      return [
        { id: `${product.id}`, product, line: plain },
        ...lines.filter((l) => l !== plain).map((line) => ({ id: `${product.id}-${line.key}`, product, line })),
      ];
    });
  });

  /**
   * En el movil se ve una parte cada vez: lo que tiene la mesa ('ticket') o la carta a pantalla completa ('menu').
   * Una mesa recien abierta (nada pedido ni apuntado) empieza en la carta; una ya abierta, en lo que tiene.
   */
  readonly tab = signal<'menu' | 'ticket'>('ticket');

  readonly draft = signal<DraftLine[]>([]);
  readonly draftCount = computed(() => this.draft().reduce((sum, l) => sum + l.quantity, 0));
  readonly draftTotal = computed(() => this.draft().reduce((sum, l) => sum + this.unitPrice(l) * l.quantity, 0));
  /** Ingredientes que se pueden anadir como extra (con su precio) */
  readonly extrasList = signal<Extra[]>([]);
  readonly sending = signal(false);
  /**
   * Lo ya pedido como en cocina: por rondas y, dentro de cada ronda, en el orden de cocina de su categoria
   * (entrantes primero...), con el color de la categoria.
   */
  readonly sentLines = computed(() => {
    const order = new Map(this.allCategories().map((c) => [c.id, c.kitchenOrder]));
    const product = new Map(this.products().map((p) => [p.id, p]));
    const rank = (l: ServiceLine) => order.get(product.get(l.productId)?.categoryId ?? -1) ?? 999;
    return [...(this.session()?.lines ?? [])].sort((a, b) => a.round - b.round || rank(a) - rank(b));
  });
  /** Se puede cobrar: hay algo pedido, todo esta servido, no queda nada sin enviar y falta dinero por cobrar */
  readonly canCharge = computed(() => {
    const session = this.session();
    return !!session && session.lines.length > 0 && session.pending > 0 && !this.draft().length
      && session.lines.every((l) => l.status === 'SERVIDO');
  });
  /** Dialogo de cobro abierto (`app-pay-dialog`, el mismo que en "Barra y cobros") */
  readonly paying = signal(false);
  /** Productos que cocina o barra ya tienen listos para llevar a la mesa */
  readonly readyLines = computed(() => (this.session()?.lines ?? []).filter((l) => l.status === 'LISTO'));
  /** Hora actual, para los minutos de cada producto ("Preparando · 7 min") */
  readonly now = signal(Date.now());

  // Editar una linea apuntada (notas e ingredientes)
  readonly editing = signal<DraftLine | null>(null);
  /** A cuantas unidades de la linea se aplica el cambio (las demas se quedan como estaban) */
  readonly editUnits = signal(1);
  editNotes = '';
  readonly editRemoved = signal<Set<number>>(new Set());
  readonly editAdded = signal<number[]>([]);
  readonly editingProduct = computed(() => this.products().find((p) => p.id === this.editing()?.productId) ?? null);

  // Dialogos de la mesa
  readonly dialog = signal<'guests' | 'tables' | null>(null);
  guestsForm = 1;
  notesForm = '';
  readonly floor = signal<FloorArea[]>([]);
  readonly tableIds = signal<Set<number>>(new Set());

  private nextKey = 1;
  private draftLoaded = false;

  constructor() {
    // Guarda lo apuntado en el navegador de este camarero
    effect(() => {
      const lines = this.draft();
      if (!this.draftLoaded) return;
      try {
        if (lines.length) sessionStorage.setItem(DRAFT_KEY + this.id(), JSON.stringify(lines));
        else sessionStorage.removeItem(DRAFT_KEY + this.id());
      } catch {
        // sin almacenamiento: la comanda dura hasta recargar
      }
    });
  }

  ngOnInit(): void {
    this.restoreDraft();
    this.service.get(this.id()).subscribe({
      next: (session) => {
        this.session.set(session);
        if (session.status === 'OPEN' && !session.lines.length && !this.draft().length) this.tab.set('menu');
      },
      error: (err) => {
        this.notify.error(apiErrorMessage(err));
        this.router.navigate(['/service']);
      },
    });
    this.productsService.search('', null, true).subscribe((products) => this.products.set(products));
    this.categoriesService.list().subscribe((categories) => this.allCategories.set(categories));
    this.ingredientsService.extras().subscribe((extras) => this.extrasList.set(extras));
    const changes = this.live.on('session:' + this.id()).subscribe(() => this.refresh());
    const timer = setInterval(() => this.refresh(), REFRESH_MS);
    const clock = setInterval(() => this.now.set(Date.now()), CLOCK_MS);
    this.destroyRef.onDestroy(() => {
      changes.unsubscribe();
      clearInterval(timer);
      clearInterval(clock);
    });
  }

  /** Cobro hecho: si la mesa ha quedado libre se vuelve a la sala; si no (pago parcial), se sigue en ella. */
  onPaid(updated: TableSession): void {
    this.paying.set(false);
    if (updated.status === 'CLOSED') {
      this.router.navigate(['/service']);
      return;
    }
    this.session.set(updated);
  }

  /** Recarga la mesa (estados de cocina) si no hay nada a medias en pantalla. */
  private refresh(): void {
    if (this.dialog() || this.editing() || this.sending() || this.paying() || this.session()?.status !== 'OPEN') return;
    this.service.get(this.id()).subscribe({ next: (session) => this.session.set(session) });
  }

  // ----- Apuntar productos -----

  /**
   * Como en la web de pedidos: tocar un producto que no esta apuntado apunta uno normal, sin dialogo.
   * Tocar uno ya apuntado abre su dialogo (notas, quitar ingredientes, extras); en las bebidas suma otro.
   */
  tapTile(tile: MenuTile): void {
    if (tile.line && this.editable(tile.product)) this.openEdit(tile.line);
    else if (tile.line) this.changeQuantity(tile.line, 1);
    else this.addPlain(tile.product);
  }

  /**
   * Tiene algo que cambiar: ingredientes, o notas para cocina. Lo que no lleva ingredientes ni pasa por cocina
   * (bebidas, en barra) no tiene dialogo: cada toque suma uno.
   */
  editable(product: Product | undefined): boolean {
    return !!product && (product.ingredients.length > 0 || product.station === 'COCINA');
  }

  /** La linea apuntada se puede editar (para la lista "Por enviar"). */
  editableLine(line: DraftLine): boolean {
    return this.editable(this.products().find((p) => p.id === line.productId));
  }

  /** "Vegetal: 2. Toca para notas e ingredientes" (para lectores de pantalla). */
  tileLabel(tile: MenuTile): string {
    const custom = tile.line ? this.lineText(tile.line) : '';
    const name = tile.product.name + (custom ? ' ' + custom : '');
    if (!tile.line) return `${name}. Toca para apuntar uno`;
    return `${name}: ${tile.line.quantity}. ${this.editable(tile.product) ? 'Toca para notas e ingredientes' : 'Toca para añadir otro'}`;
  }

  /** Lo que cambia en una linea apuntada: "con huevo · sin Cebolla · poco hecha". */
  lineText(line: DraftLine): string {
    return this.extras(this.removedNames(line), line.notes, this.addedNames(line));
  }

  /** Apunta una unidad sin cambios: si ya hay una linea igual, suma uno. */
  private addPlain(product: Product): void {
    const same = this.draft().find((l) => l.productId === product.id && isPlain(l));
    if (same) {
      this.changeQuantity(same, 1);
      return;
    }
    this.draft.update((lines) => [...lines, this.newLine(product)]);
  }

  private newLine(product: Product): DraftLine {
    return {
      key: this.nextKey++,
      productId: product.id,
      name: product.name,
      color: product.displayColor,
      price: product.price,
      quantity: 1,
      notes: '',
      removedIds: [],
      addedIds: [],
    };
  }

  changeQuantity(line: DraftLine, delta: number): void {
    this.draft.update((lines) =>
      lines
        .map((l) => (l.key === line.key ? { ...l, quantity: Math.min(99, l.quantity + delta) } : l))
        .filter((l) => l.quantity > 0),
    );
  }

  openEdit(line: DraftLine): void {
    this.editUnits.set(1);
    this.editNotes = line.notes;
    this.editRemoved.set(new Set(line.removedIds));
    this.editAdded.set([...line.addedIds]);
    this.editing.set(line);
  }

  toggleIngredient(id: number): void {
    const set = new Set(this.editRemoved());
    if (set.has(id)) set.delete(id);
    else set.add(id);
    this.editRemoved.set(set);
  }

  changeUnits(delta: number): void {
    const max = this.editing()?.quantity ?? 1;
    this.editUnits.update((n) => Math.min(max, Math.max(1, n + delta)));
  }

  /**
   * Guarda notas, ingredientes quitados y extras. Si se aplica solo a algunas unidades, esas pasan a una linea
   * aparte (otro boton en la carta, al lado). Las lineas que quedan iguales se juntan: dos "sin cebolla" suman en una.
   */
  saveEdit(): void {
    const line = this.editing();
    if (!line) return;
    const changes = {
      notes: this.editNotes.trim(),
      removedIds: [...this.editRemoved()].sort((a, b) => a - b),
      addedIds: this.editAdded(),
    };
    const units = Math.min(this.editUnits(), line.quantity);
    this.editing.set(null);
    const lines: DraftLine[] = [];
    for (const l of this.draft()) {
      if (l.key !== line.key) lines.push(l);
      else if (units >= l.quantity) lines.push({ ...l, ...changes });
      else lines.push({ ...l, quantity: l.quantity - units }, { ...l, ...changes, key: this.nextKey++, quantity: units });
    }
    const merged: DraftLine[] = [];
    for (const l of lines) {
      const same = merged.find((m) => m.productId === l.productId && sameChanges(m, l));
      if (same) same.quantity = Math.min(99, same.quantity + l.quantity);
      else merged.push({ ...l });
    }
    this.draft.set(merged);
  }

  removeDraft(line: DraftLine): void {
    this.draft.update((lines) => lines.filter((l) => l.key !== line.key));
    this.editing.set(null);
  }

  toggleExtra(id: number): void {
    this.editAdded.update((ids) => toggleId(ids, id));
  }

  /** Precio de una unidad apuntada: el producto mas sus extras. */
  unitPrice(line: DraftLine): number {
    return line.price + extrasPrice(this.extrasList(), line.addedIds);
  }

  /** Color de la categoria de lo pedido (el mismo que en cocina); si no se conoce, el del producto. */
  categoryColor(line: ServiceLine): string {
    return this.products().find((p) => p.id === line.productId)?.categoryColor ?? line.color;
  }

  extraNames(extras: LineExtra[]): string[] {
    return extras.map((e) => e.name);
  }

  addedNames(line: DraftLine): string[] {
    return this.extrasList().filter((e) => line.addedIds.includes(e.id)).map((e) => e.name);
  }

  /** Texto corto de lo que cambia en una linea: "con huevo · sin Cebolla · poco hecha". */
  extras(removed: string[], notes: string | null, added: string[] = []): string {
    return [...added.map((a) => 'con ' + a.toLowerCase()), ...removed.map((r) => 'sin ' + r), ...(notes ? [notes] : [])].join(' · ');
  }

  removedNames(line: DraftLine): string[] {
    const product = this.products().find((p) => p.id === line.productId);
    return line.removedIds.map((id) => product?.ingredients.find((i) => i.id === id)?.name ?? '?');
  }

  // ----- Enviar y anular -----

  send(): void {
    const session = this.session();
    if (!session || !this.draft().length || this.sending()) return;
    this.sending.set(true);
    const lines = this.draft().map((l) => ({
      productId: l.productId,
      quantity: l.quantity,
      notes: l.notes || null,
      removedIngredientIds: l.removedIds,
      addedIngredientIds: l.addedIds,
    }));
    this.service.sendRound(session.id, lines).subscribe({
      next: (updated) => {
        this.session.set(updated);
        this.draft.set([]);
        this.sending.set(false);
        // Enviado: de vuelta a lo que tiene la mesa (en el movil se cierra la carta)
        this.tab.set('ticket');
        this.notify.success(`Ronda ${updated.rounds} enviada a cocina`);
      },
      error: (err) => {
        this.sending.set(false);
        this.notify.error(apiErrorMessage(err));
      },
    });
  }

  /** Minutos que lleva un producto en su estado (pendiente o preparandose); null si ya esta listo o servido. */
  waitingMinutes(line: ServiceLine): number | null {
    if (line.status !== 'PENDIENTE' && line.status !== 'PREPARANDO') return null;
    const since = new Date(line.statusChangedAt ?? line.createdAt).getTime();
    return Math.max(0, Math.floor((this.now() - since) / 60_000));
  }

  /** Como en cocina: a partir de 10 minutos, aviso; de 20, tarde. */
  waitingClass(minutes: number): string {
    return minutes >= 20 ? 'late' : minutes >= 10 ? 'warn' : '';
  }

  /** Se puede anular mientras cocina no lo ha empezado (o si no se prepara y aun no se ha servido). */
  cancellable(line: ServiceLine): boolean {
    return line.status === 'PENDIENTE' || (line.station === 'NINGUNA' && line.status !== 'SERVIDO');
  }

  /** Marca como servidos (llevados a la mesa) los productos indicados. */
  serve(lines: ServiceLine[]): void {
    const session = this.session();
    if (!session || !lines.length) return;
    const ids = new Set(lines.map((l) => l.id));
    this.session.set({ ...session, lines: session.lines.map((l) => (ids.has(l.id) ? { ...l, status: 'SERVIDO' } : l)) });
    this.kitchen.move(lines.map((l) => ({ source: 'MESA' as const, id: l.id })), 'SERVIDO').subscribe({
      error: (err) => {
        this.notify.error(apiErrorMessage(err));
        this.service.get(session.id).subscribe((fresh) => this.session.set(fresh));
      },
    });
  }

  async removeSent(line: ServiceLine): Promise<void> {
    const session = this.session();
    if (!session) return;
    const accepted = await this.confirmService.ask({
      title: 'Anular producto',
      message: `¿Quitar ${line.quantity} × ${line.productName} de la cuenta?`,
      confirmText: 'Anular',
    });
    if (!accepted) return;
    this.service.removeLine(session.id, line.id).subscribe({
      next: (updated) => this.session.set(updated),
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  // ----- Comensales y mesas -----

  openGuests(): void {
    const session = this.session();
    if (!session) return;
    this.guestsForm = session.guests;
    this.notesForm = session.notes ?? '';
    this.dialog.set('guests');
  }

  changeGuestsForm(delta: number): void {
    this.guestsForm = Math.min(99, Math.max(1, this.guestsForm + delta));
  }

  saveGuests(): void {
    const session = this.session();
    if (!session) return;
    this.service.update(session.id, this.guestsForm, this.notesForm.trim() || null).subscribe({
      next: (updated) => {
        this.session.set(updated);
        this.dialog.set(null);
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  openTables(): void {
    const session = this.session();
    if (!session) return;
    this.tableIds.set(new Set(session.tables.map((t) => t.id)));
    this.service.floor().subscribe({
      next: (floor) => {
        this.floor.set(floor);
        this.dialog.set('tables');
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  toggleTable(id: number): void {
    const set = new Set(this.tableIds());
    if (set.has(id)) set.delete(id);
    else set.add(id);
    this.tableIds.set(set);
  }

  saveTables(): void {
    const session = this.session();
    if (!session || !this.tableIds().size) return;
    this.service.changeTables(session.id, [...this.tableIds()]).subscribe({
      next: (updated) => {
        this.session.set(updated);
        this.dialog.set(null);
        this.notify.success(`Ahora en la mesa ${updated.tableLabel}`);
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  private restoreDraft(): void {
    try {
      const saved = sessionStorage.getItem(DRAFT_KEY + this.id());
      // Comandas guardadas antes de los extras no tienen addedIds
      const lines = saved ? (JSON.parse(saved) as DraftLine[]).map((l) => ({ ...l, addedIds: l.addedIds ?? [] })) : [];
      this.draft.set(lines);
      this.nextKey = Math.max(0, ...lines.map((l) => l.key)) + 1;
    } catch {
      // comanda guardada ilegible: se empieza de cero
    }
    this.draftLoaded = true;
  }
}

function normalize(text: string): string {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
}

/** Sin notas, sin ingredientes quitados y sin extras: el producto tal cual. */
function isPlain(line: DraftLine): boolean {
  return !line.notes && !line.removedIds.length && !line.addedIds.length;
}

/** Mismas notas, mismos ingredientes quitados y mismos extras: dos lineas del mismo producto se pueden juntar. */
function sameChanges(a: DraftLine, b: DraftLine): boolean {
  return a.notes === b.notes && a.removedIds.join() === b.removedIds.join() && a.addedIds.join() === b.addedIds.join();
}
