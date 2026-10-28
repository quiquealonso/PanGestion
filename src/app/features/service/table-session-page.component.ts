import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Product } from '../../core/models/product.model';
import { FloorArea, ITEM_STATUS_LABELS, ServiceLine, TableSession } from '../../core/models/table-service.model';
import { KitchenService } from '../../core/services/kitchen.service';
import { LiveEventsService } from '../../core/services/live-events.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NotificationService } from '../../core/services/notification.service';
import { ProductsService } from '../../core/services/products.service';
import { TableServiceService } from '../../core/services/table-service.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { Extra, LineExtra } from '../../core/models/ingredient.model';
import { IngredientsService } from '../../core/services/ingredients.service';
import { ExtrasPickerComponent, extrasPrice, toggleId } from '../../shared/extras-picker.component';
import { ModalComponent } from '../../shared/modal/modal.component';

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

const DRAFT_KEY = 'pangestion.draft.';
/** Respaldo: la mesa se refresca con los avisos en tiempo real; esto solo cubre si se corta la conexion. */
const REFRESH_MS = 60_000;

/**
 * Comanda de una mesa (pensada para el movil del camarero): se tocan productos de la carta y se envian
 * a cocina por rondas. Debajo, lo ya pedido y el total. Los productos son de toda la mesa: los comensales
 * solo cuentan para dividir la cuenta al cobrar.
 * Lo apuntado y no enviado se guarda en el navegador, para no perderlo si se recarga la pagina.
 */
@Component({
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, ModalComponent, ExtrasPickerComponent],
  selector: 'app-table-session-page',
  templateUrl: './table-session-page.component.html',
  styleUrl: './table-session-page.component.scss',
})
export class TableSessionPageComponent implements OnInit {
  private readonly service = inject(TableServiceService);
  private readonly productsService = inject(ProductsService);
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
  readonly categoryId = signal<number | null>(null);
  readonly search = signal('');
  readonly visibleProducts = computed(() => {
    const text = normalize(this.search());
    const category = this.categoryId();
    return this.products().filter(
      (p) => (text ? normalize(p.name).includes(text) : category === null || p.categoryId === category),
    );
  });

  /** En el movil se ve una parte cada vez */
  readonly tab = signal<'menu' | 'ticket'>('menu');

  readonly draft = signal<DraftLine[]>([]);
  readonly draftCount = computed(() => this.draft().reduce((sum, l) => sum + l.quantity, 0));
  readonly draftTotal = computed(() => this.draft().reduce((sum, l) => sum + this.unitPrice(l) * l.quantity, 0));
  /** Ingredientes que se pueden anadir como extra (con su precio) */
  readonly extrasList = signal<Extra[]>([]);
  readonly sending = signal(false);
  /** Productos que cocina o barra ya tienen listos para llevar a la mesa */
  readonly readyLines = computed(() => (this.session()?.lines ?? []).filter((l) => l.status === 'LISTO'));

  // Editar una linea apuntada (notas e ingredientes)
  readonly editing = signal<DraftLine | null>(null);
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
      next: (session) => this.session.set(session),
      error: (err) => {
        this.notify.error(apiErrorMessage(err));
        this.router.navigate(['/service']);
      },
    });
    this.productsService.search('', null, true).subscribe((products) => this.products.set(products));
    this.ingredientsService.extras().subscribe((extras) => this.extrasList.set(extras));
    const changes = this.live.on('session:' + this.id()).subscribe(() => this.refresh());
    const timer = setInterval(() => this.refresh(), REFRESH_MS);
    this.destroyRef.onDestroy(() => {
      changes.unsubscribe();
      clearInterval(timer);
    });
  }

  /** Recarga la mesa (estados de cocina) si no hay nada a medias en pantalla. */
  private refresh(): void {
    if (this.dialog() || this.editing() || this.sending() || this.session()?.status !== 'OPEN') return;
    this.service.get(this.id()).subscribe({ next: (session) => this.session.set(session) });
  }

  // ----- Apuntar productos -----

  add(product: Product): void {
    const same = this.draft().find((l) => l.productId === product.id && !l.notes && !l.removedIds.length && !l.addedIds.length);
    if (same) {
      this.changeQuantity(same, 1);
      return;
    }
    const line: DraftLine = {
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
    this.draft.update((lines) => [...lines, line]);
  }

  /** Unidades apuntadas de un producto (se ven sobre el boton). */
  countFor(productId: number): number {
    return this.draft()
      .filter((l) => l.productId === productId)
      .reduce((sum, l) => sum + l.quantity, 0);
  }

  changeQuantity(line: DraftLine, delta: number): void {
    this.draft.update((lines) =>
      lines
        .map((l) => (l.key === line.key ? { ...l, quantity: Math.min(99, l.quantity + delta) } : l))
        .filter((l) => l.quantity > 0),
    );
  }

  openEdit(line: DraftLine): void {
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

  saveEdit(): void {
    const line = this.editing();
    if (!line) return;
    const changes = {
      notes: this.editNotes.trim(),
      removedIds: [...this.editRemoved()].sort((a, b) => a - b),
      addedIds: this.editAdded(),
    };
    this.draft.update((lines) => lines.map((l) => (l.key === line.key ? { ...l, ...changes } : l)));
    this.editing.set(null);
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
        this.notify.success(`Ronda ${updated.rounds} enviada a cocina`);
      },
      error: (err) => {
        this.sending.set(false);
        this.notify.error(apiErrorMessage(err));
      },
    });
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

  // ----- Comensales, mesas y liberar -----

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

  async close(): Promise<void> {
    const session = this.session();
    if (!session) return;
    if (session.pending > 0) {
      this.notify.error(`Quedan ${session.pending.toFixed(2).replace('.', ',')} € por cobrar: cóbrala en «Barra y cobros»`);
      return;
    }
    const pending = this.draftCount() ? ` Hay ${this.draftCount()} productos apuntados sin enviar que se perderán.` : '';
    const accepted = await this.confirmService.ask({
      title: `Liberar mesa ${session.tableLabel}`,
      message: `Los clientes se han ido y la mesa queda libre (todo cobrado: ${session.total.toFixed(2).replace('.', ',')} €).${pending}`,
      confirmText: 'Liberar mesa',
      danger: false,
    });
    if (!accepted) return;
    this.service.close(session.id).subscribe({
      next: () => {
        this.draft.set([]);
        this.notify.success(`Mesa ${session.tableLabel} libre`);
        this.router.navigate(['/service']);
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
