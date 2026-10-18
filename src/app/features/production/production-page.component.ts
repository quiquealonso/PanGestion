import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Company } from '../../core/models/company.model';
import { ProductionRow } from '../../core/models/order.model';
import { MyCompanyService } from '../../core/services/my-company.service';
import { NotificationService } from '../../core/services/notification.service';
import { OrdersService } from '../../core/services/orders.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { addDays, toIsoDate } from '../../core/utils/dates';

/** Cada cuanto se recargan los datos solos ("en vivo"). */
const REFRESH_MS = 30_000;

interface Slot {
  label: string;
  start: number; // minutos desde medianoche
}

interface MatrixRow {
  key: string;
  name: string;
  removed: string[];
  category: string;
  categoryColor: string;
  /** El primer producto de su categoria lleva la cabecera de grupo. */
  startsCategory: boolean;
  cells: number[];
  total: number;
}

/**
 * Pantalla de produccion para el obrador: que productos hay que preparar y para que franja de recogida.
 * Filas = productos (agrupados por categoria), columnas = franjas horarias. Se actualiza sola.
 */
@Component({
  imports: [DatePipe, FormsModule],
  selector: 'app-production-page',
  templateUrl: './production-page.component.html',
  styleUrl: './production-page.component.scss',
})
export class ProductionPageComponent implements OnInit {
  private readonly ordersService = inject(OrdersService);
  private readonly myCompanyService = inject(MyCompanyService);
  private readonly notify = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly today = toIsoDate();
  readonly day = signal(toIsoDate());
  /** Horas por franja (columna). */
  readonly slotHours = signal(1);
  /** true = solo lo que falta por entregar; false = todo el dia. */
  readonly onlyPending = signal(true);
  /** false = se ocultan las franjas sin nada que preparar (menos la actual). */
  readonly showEmptySlots = signal(false);
  readonly rows = signal<ProductionRow[]>([]);
  readonly company = signal<Company | null>(null);
  readonly updatedAt = signal<Date | null>(null);
  readonly now = signal(new Date());

  /** Franjas desde la apertura hasta el cierre de la empresa. */
  readonly slots = computed<Slot[]>(() => {
    const company = this.company();
    const open = company ? toMinutes(company.openingTime) : 7 * 60;
    const close = company ? toMinutes(company.closingTime) : 21 * 60;
    const size = this.slotHours() * 60;
    const first = Math.floor(open / 60) * 60;
    const slots: Slot[] = [];
    for (let start = first; start < close; start += size) {
      slots.push({ start, label: `${hour(start)}–${hour(Math.min(start + size, 24 * 60))}` });
    }
    return slots;
  });

  /** Tabla producto x franja con totales. */
  readonly matrix = computed<MatrixRow[]>(() => {
    const slots = this.slots();
    const result: MatrixRow[] = [];
    for (const row of this.rows()) {
      const key = `${row.productId}|${row.removedIngredients.join(',')}`;
      let line = result.find((r) => r.key === key);
      if (!line) {
        line = {
          key,
          name: row.productName,
          removed: row.removedIngredients,
          category: row.categoryName,
          categoryColor: row.categoryColor,
          startsCategory: result[result.length - 1]?.category !== row.categoryName,
          cells: slots.map(() => 0),
          total: 0,
        };
        result.push(line);
      }
      line.cells[this.slotIndex(row.pickupTime)] += row.quantity;
      line.total += row.quantity;
    }
    return result;
  });

  readonly columnTotals = computed(() => this.slots().map((_, i) => this.matrix().reduce((sum, r) => sum + r.cells[i], 0)));
  readonly grandTotal = computed(() => this.matrix().reduce((sum, r) => sum + r.total, 0));
  readonly maxCell = computed(() => Math.max(1, ...this.matrix().flatMap((r) => r.cells)));

  /** Indices de las franjas que se pintan como columnas. */
  readonly visibleSlots = computed(() => {
    const totals = this.columnTotals();
    return this.slots()
      .map((_, i) => i)
      .filter((i) => this.showEmptySlots() || totals[i] > 0 || i === this.currentSlot());
  });

  /** Columna de la franja actual (solo si se esta viendo hoy). */
  readonly currentSlot = computed(() => {
    if (this.day() !== this.today) return -1;
    const minutes = this.now().getHours() * 60 + this.now().getMinutes();
    return this.slots().findIndex((s) => minutes >= s.start && minutes < s.start + this.slotHours() * 60);
  });

  /** Unidades de la franja actual y de la siguiente: lo urgente. */
  readonly nextUnits = computed(() => {
    const current = this.currentSlot();
    if (current < 0) return null;
    const totals = this.columnTotals();
    return (totals[current] ?? 0) + (totals[current + 1] ?? 0);
  });

  ngOnInit(): void {
    this.myCompanyService.get().subscribe((company) => this.company.set(company));
    this.load();
    const timer = setInterval(() => {
      this.now.set(new Date());
      this.load();
    }, REFRESH_MS);
    this.destroyRef.onDestroy(() => clearInterval(timer));
  }

  load(): void {
    this.ordersService.production(this.day(), this.onlyPending()).subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.updatedAt.set(new Date());
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  setDay(day: string): void {
    if (!day) return;
    this.day.set(day);
    this.load();
  }

  moveDay(delta: number): void {
    this.setDay(addDays(delta, new Date(this.day() + 'T00:00')));
  }

  setOnlyPending(value: boolean): void {
    this.onlyPending.set(value);
    this.load();
  }

  /** Intensidad de la celda segun la cantidad (un solo tono: mas oscuro = mas unidades). */
  cellAlpha(value: number): number {
    return value === 0 ? 0 : 0.14 + 0.5 * (value / this.maxCell());
  }

  /** "Bacon" -> "sin bacon". Es una funcion flecha para poder usarla en la plantilla con map(). */
  readonly nameWithout = (ingredient: string) => 'sin ' + ingredient.toLowerCase();

  /** Pantalla completa, para dejarla puesta en el obrador. */
  fullscreen(element: HTMLElement): void {
    if (document.fullscreenElement) document.exitFullscreen();
    else element.requestFullscreen?.();
  }

  private slotIndex(time: string): number {
    const minutes = toMinutes(time);
    const slots = this.slots();
    const index = slots.findIndex((s) => minutes >= s.start && minutes < s.start + this.slotHours() * 60);
    // Si la hora queda fuera del horario actual (se cambio despues), va a la primera o ultima columna
    return index >= 0 ? index : minutes < (slots[0]?.start ?? 0) ? 0 : slots.length - 1;
  }
}

function toMinutes(time: string): number {
  const [h, m] = time.split(':').map(Number);
  return h * 60 + m;
}

function hour(minutes: number): string {
  return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;
}
