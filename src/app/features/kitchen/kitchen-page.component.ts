import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { KitchenItem, KitchenTicket } from '../../core/models/kitchen.model';
import { Station } from '../../core/models/product.model';
import { ItemStatus } from '../../core/models/table-service.model';
import { KitchenService } from '../../core/services/kitchen.service';
import { LiveEventsService } from '../../core/services/live-events.service';
import { NotificationService } from '../../core/services/notification.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { beep } from '../../shared/sound';

/** Respaldo: la pantalla se refresca con los avisos en tiempo real; esto solo cubre si se corta la conexion. */
const REFRESH_MS = 30_000;
/** Cada cuanto se actualizan los minutos de cada ticket. */
const CLOCK_MS = 15_000;
const STATION_KEY = 'pangestion.kitchen-station';

interface Column {
  status: ItemStatus;
  title: string;
  next: ItemStatus | null;
  previous: ItemStatus | null;
}

/** Columnas de la cocina: un toque en un producto lo pasa a la siguiente. */
const COLUMNS: Column[] = [
  { status: 'PENDIENTE', title: 'Pendiente', next: 'PREPARANDO', previous: null },
  { status: 'PREPARANDO', title: 'Preparando', next: 'LISTO', previous: 'PENDIENTE' },
  { status: 'LISTO', title: 'Listo para servir', next: null, previous: 'PREPARANDO' },
];

/** En la barra no se prepara nada (es llevar la bebida): de pendiente pasa directamente a listo para servir. */
const BAR_COLUMNS: Column[] = [
  { status: 'PENDIENTE', title: 'Pendiente', next: 'LISTO', previous: null },
  { status: 'LISTO', title: 'Listo para servir', next: null, previous: 'PENDIENTE' },
];

/** Parte de un ticket que cae en una columna (sus productos con ese estado). */
interface Fragment {
  ticket: KitchenTicket;
  items: KitchenItem[];
}

/**
 * Pantalla de cocina y barra (tactil): tickets de mesas y pedidos para llevar en tres columnas.
 * Tocar un producto lo pasa a la columna siguiente; "Todo" pasa el ticket entero; la flecha lo devuelve.
 * Lo listo de las mesas lo recoge el camarero (al servirlo desaparece de aqui).
 */
@Component({
  imports: [DatePipe],
  selector: 'app-kitchen-page',
  templateUrl: './kitchen-page.component.html',
  styleUrl: './kitchen-page.component.scss',
})
export class KitchenPageComponent implements OnInit {
  private readonly kitchen = inject(KitchenService);
  private readonly notify = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly live = inject(LiveEventsService);
  /** Ids provisionales (negativos) de las unidades movidas hasta que llega la recarga */
  private tempIds = 0;
  /** Tickets ya vistos: si llega uno nuevo, suena */
  private seen: Set<string> | null = null;

  readonly station = signal<Station>(readStation());
  readonly columns = computed(() => (this.station() === 'BARRA' ? BAR_COLUMNS : COLUMNS));
  readonly tickets = signal<KitchenTicket[]>([]);
  readonly loaded = signal(false);
  readonly now = signal(Date.now());

  readonly byColumn = computed(() => {
    const result = new Map<ItemStatus, Fragment[]>();
    const bar = this.station() === 'BARRA';
    for (const column of this.columns()) {
      // En la barra, lo que quedo "preparandose" (de antes de quitar ese paso) sale con lo pendiente
      const matches = (status: ItemStatus) =>
        status === column.status || (bar && column.status === 'PENDIENTE' && status === 'PREPARANDO');
      result.set(
        column.status,
        this.tickets()
          // Dentro del ticket, por el orden de cocina de la categoria (entrantes primero)
          .map((ticket) => ({
            ticket,
            items: ticket.items.filter((i) => matches(i.status)).sort((a, b) => a.categoryOrder - b.categoryOrder),
          }))
          .filter((f) => f.items.length),
      );
    }
    return result;
  });
  /**
   * Ancho de Pendiente y Preparando: cada una pide tanto sitio como tickets tiene (de 1 a 3 por fila),
   * asi la que tiene mas trabajo se ensancha y la otra se estrecha. Listo es una sola columna fija.
   * En la barra solo hay Pendiente (todo el ancho) y Listo.
   */
  readonly boardColumns = computed(() => {
    if (this.station() === 'BARRA') return 'minmax(0, 1fr) var(--ready-width)';
    const slots = (status: ItemStatus) => Math.min(3, Math.max(1, this.byColumn().get(status)?.length ?? 0));
    return `minmax(0, ${slots('PENDIENTE')}fr) minmax(0, ${slots('PREPARANDO')}fr) var(--ready-width)`;
  });
  readonly counts = computed(() => {
    const counts: Record<string, number> = {};
    for (const column of this.columns()) {
      counts[column.status] = (this.byColumn().get(column.status) ?? [])
        .reduce((sum, f) => sum + f.items.reduce((s, i) => s + i.quantity, 0), 0);
    }
    return counts;
  });

  ngOnInit(): void {
    this.load();
    const changes = this.live.on('kitchen').subscribe(() => this.load());
    const clock = setInterval(() => this.now.set(Date.now()), CLOCK_MS);
    const fallback = setInterval(() => this.load(), REFRESH_MS);
    this.destroyRef.onDestroy(() => {
      changes.unsubscribe();
      clearInterval(clock);
      clearInterval(fallback);
    });
  }

  load(): void {
    this.kitchen.tickets(this.station()).subscribe({
      next: (tickets) => {
        const keys = new Set(tickets.map((t) => `${t.source}${t.refId}/${t.round}`));
        if (this.seen && [...keys].some((k) => !this.seen!.has(k))) beep(3);
        this.seen = keys;
        this.tickets.set(tickets);
        this.loaded.set(true);
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  setStation(station: Station): void {
    this.station.set(station);
    this.tickets.set([]);
    this.loaded.set(false);
    this.seen = null;
    try {
      localStorage.setItem(STATION_KEY, station);
    } catch {
      // sin almacenamiento: vale para esta visita
    }
    this.load();
  }

  /**
   * Mueve productos a otro estado: una unidad (tocar un producto) o todas ("Todo").
   * Se ve al momento; si el servidor no lo acepta, se recarga.
   */
  move(items: KitchenItem[], status: ItemStatus | null, units?: number): void {
    // Las unidades recien movidas aun no tienen id del servidor: llega con la recarga (al momento)
    items = items.filter((i) => i.id > 0);
    if (!status || !items.length) return;
    const now = new Date().toISOString();
    this.tickets.update((tickets) =>
      tickets.map((t) => {
        let lines = t.items;
        for (const item of items) {
          if (!lines.some((i) => i.source === item.source && i.id === item.id)) continue;
          const moving = Math.min(units ?? item.quantity, item.quantity);
          // Lo que se mueve se junta con lo mismo que ya este en ese estado, como hace el servidor
          const twin = lines.find((i) => i.status === status && sameDish(i, item));
          lines = lines
            .map((i) => {
              if (i === twin) return { ...i, quantity: i.quantity + moving, statusChangedAt: now };
              if (i.source === item.source && i.id === item.id) {
                return moving === i.quantity && !twin ? { ...i, status, statusChangedAt: now } : { ...i, quantity: i.quantity - moving };
              }
              return i;
            })
            .filter((i) => i.quantity > 0);
          if (!twin && moving < item.quantity) {
            lines = [...lines, { ...item, id: -++this.tempIds, quantity: moving, status, statusChangedAt: now }];
          }
        }
        return lines === t.items ? t : { ...t, items: lines };
      }),
    );
    this.kitchen.move(items.map((i) => ({ source: i.source, id: i.id, units })), status).subscribe({
      error: (err) => {
        this.notify.error(apiErrorMessage(err));
        this.load();
      },
    });
  }

  /** Inicial de la categoria (P de Pizzas) para distinguir productos que se llaman igual. */
  initial(item: KitchenItem): string {
    return item.categoryName.trim().charAt(0).toUpperCase();
  }

  /** El boton "Todo" solo tiene sentido si hay mas de una unidad en esa parte del ticket. */
  hasSeveral(items: KitchenItem[]): boolean {
    return items.length > 1 || items[0].quantity > 1;
  }

  /** Nombre del cliente de un pedido para llevar (va delante en el detalle). */
  customer(ticket: KitchenTicket): string {
    return ticket.detail.split(' · ')[0];
  }

  /** Minutos desde una hora (para el color de urgencia). */
  minutes(time: string | null): number {
    return time ? Math.max(0, Math.floor((this.now() - new Date(time).getTime()) / 60_000)) : 0;
  }

  /**
   * Urgencia de un ticket. Mesas: a partir de 10 minutos esperando, aviso; de 20, tarde.
   * Para llevar: aviso si faltan 15 minutos o menos para la recogida; tarde si ya ha pasado.
   */
  urgency(ticket: KitchenTicket): string {
    if (ticket.source === 'LLEVAR') {
      const left = (new Date(ticket.time).getTime() - this.now()) / 60_000;
      return left < 0 ? 'late' : left <= 15 ? 'warn' : '';
    }
    const minutes = this.minutes(ticket.time);
    return minutes >= 20 ? 'late' : minutes >= 10 ? 'warn' : '';
  }

  toggleFullscreen(): void {
    if (document.fullscreenElement) document.exitFullscreen();
    else document.documentElement.requestFullscreen?.();
  }
}

function readStation(): Station {
  try {
    const saved = localStorage.getItem(STATION_KEY);
    if (saved === 'COCINA' || saved === 'BARRA') return saved;
  } catch {
    // sin almacenamiento
  }
  return 'COCINA';
}

/** Lo mismo pedido (mismo producto, notas e ingredientes quitados): se junta en una fila. */
function sameDish(a: KitchenItem, b: KitchenItem): boolean {
  return (
    a.source === b.source &&
    a.productName === b.productName &&
    a.categoryName === b.categoryName &&
    a.notes === b.notes &&
    a.removedIngredients.join() === b.removedIngredients.join() &&
    a.extras.join() === b.extras.join()
  );
}
