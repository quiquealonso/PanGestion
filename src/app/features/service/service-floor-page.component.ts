import { CurrencyPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { FloorArea, FloorTable } from '../../core/models/table-service.model';
import { LiveEventsService } from '../../core/services/live-events.service';
import { NotificationService } from '../../core/services/notification.service';
import { TableServiceService } from '../../core/services/table-service.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { DiningTableComponent } from '../../shared/dining-table.component';
import { ModalComponent } from '../../shared/modal/modal.component';

/** Respaldo: el plano se refresca con los avisos en tiempo real; esto solo cubre si se corta la conexion. */
const REFRESH_MS = 60_000;
/** Cada cuanto se actualizan los minutos de cada mesa. */
const CLOCK_MS = 30_000;
const VIEW_KEY = 'pangestion.service-view';
const ROTATED_KEY = 'pangestion.service-rotated';

/**
 * Servicio de mesas: plano en vivo de la sala. Las mesas ocupadas se ven en color con sus comensales
 * y el tiempo que llevan. Pulsar una mesa libre la abre (se pueden juntar varias); una ocupada lleva a su comanda.
 */
@Component({
  imports: [CurrencyPipe, FormsModule, DiningTableComponent, ModalComponent],
  selector: 'app-service-floor-page',
  templateUrl: './service-floor-page.component.html',
  styleUrl: './service-floor-page.component.scss',
})
export class ServiceFloorPageComponent implements OnInit {
  private readonly service = inject(TableServiceService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly live = inject(LiveEventsService);
  /** Se puede pedir permiso para las notificaciones del sistema (avisos con la pantalla bloqueada o en otra pestana) */
  readonly canAskNotifications = signal('Notification' in window && Notification.permission === 'default');

  readonly loading = signal(true);
  readonly areas = signal<FloorArea[]>([]);
  readonly activeId = signal<number | null>(null);
  readonly active = computed(() => this.areas().find((a) => a.id === this.activeId()) ?? this.areas()[0] ?? null);
  readonly view = signal<'map' | 'list'>(readView());
  /** Plano girado 90 grados (en el movil, uno apaisado aprovecha mejor la pantalla en vertical). Se recuerda. */
  readonly rotated = signal(readFlag(ROTATED_KEY));
  /**
   * Lo que se pinta de la zona activa. Girado en el sentido de las agujas del reloj: se recolocan las mesas
   * (no se gira la imagen), asi los nombres y las sillas siguen derechos.
   */
  readonly plan = computed(() => {
    const area = this.active();
    if (!area || !this.rotated()) return area;
    return {
      ...area,
      width: area.height,
      height: area.width,
      tables: area.tables.map((t) => ({ ...t, x: area.height - t.y - t.height, y: t.x, width: t.height, height: t.width })),
    };
  });
  /** Hora actual, para los minutos que lleva cada mesa */
  readonly now = signal(Date.now());

  // Abrir mesa
  readonly opening = signal<FloorTable | null>(null);
  readonly joinIds = signal<Set<number>>(new Set());
  guests = 2;
  notes = '';
  /** Otras mesas libres de la misma zona, para juntarlas */
  readonly joinable = computed(() => {
    const table = this.opening();
    return table ? (this.active()?.tables ?? []).filter((t) => t.id !== table.id && !t.session) : [];
  });

  ngOnInit(): void {
    this.load();
    const changes = this.live.on('floor').subscribe(() => this.load());
    const clock = setInterval(() => this.now.set(Date.now()), CLOCK_MS);
    const fallback = setInterval(() => {
      if (!this.opening()) this.load();
    }, REFRESH_MS);
    this.destroyRef.onDestroy(() => {
      changes.unsubscribe();
      clearInterval(clock);
      clearInterval(fallback);
    });
  }

  async askNotifications(): Promise<void> {
    await Notification.requestPermission();
    this.canAskNotifications.set(Notification.permission === 'default');
  }

  load(): void {
    this.service.floor().subscribe({
      next: (areas) => {
        this.areas.set(areas);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.notify.error(apiErrorMessage(err));
      },
    });
  }

  setView(view: 'map' | 'list'): void {
    this.view.set(view);
    try {
      localStorage.setItem(VIEW_KEY, view);
    } catch {
      // sin almacenamiento: vale para esta visita
    }
  }

  toggleRotated(): void {
    this.rotated.update((r) => !r);
    try {
      localStorage.setItem(ROTATED_KEY, String(this.rotated()));
    } catch {
      // sin almacenamiento: vale para esta visita
    }
  }

  /** Mesa libre: abrirla. Ocupada: ir a su comanda. */
  pick(table: FloorTable): void {
    if (table.session) {
      this.router.navigate(['/service', table.session.id]);
      return;
    }
    this.guests = Math.min(Math.max(table.seats, 1), 99);
    this.notes = '';
    this.joinIds.set(new Set());
    this.opening.set(table);
  }

  toggleJoin(table: FloorTable): void {
    const ids = new Set(this.joinIds());
    if (ids.has(table.id)) ids.delete(table.id);
    else ids.add(table.id);
    this.joinIds.set(ids);
  }

  changeGuests(delta: number): void {
    this.guests = Math.min(99, Math.max(1, this.guests + delta));
  }

  open(): void {
    const table = this.opening();
    if (!table) return;
    this.service.open([table.id, ...this.joinIds()], this.guests, this.notes.trim() || null).subscribe({
      next: (session) => {
        this.opening.set(null);
        this.router.navigate(['/service', session.id]);
      },
      error: (err) => {
        this.notify.error(apiErrorMessage(err));
        this.load();
      },
    });
  }

  /** Segunda linea de una mesa ocupada: comensales y minutos. */
  info(table: FloorTable): string | null {
    if (!table.session) return null;
    const ready = table.session.readyCount ? ` · ${table.session.readyCount} ✓` : '';
    return `${table.session.guests} pers · ${this.minutes(table.session.openedAt)}'${ready}`;
  }

  minutes(openedAt: string): number {
    return Math.max(0, Math.floor((this.now() - new Date(openedAt).getTime()) / 60_000));
  }
}

function readFlag(key: string): boolean {
  try {
    return localStorage.getItem(key) === 'true';
  } catch {
    return false;
  }
}

function readView(): 'map' | 'list' {
  try {
    const saved = localStorage.getItem(VIEW_KEY);
    if (saved === 'map' || saved === 'list') return saved;
  } catch {
    // sin almacenamiento
  }
  // En el movil el plano queda diminuto: por defecto, la lista (se puede cambiar y se recuerda)
  return window.matchMedia?.('(max-width: 720px)').matches ? 'list' : 'map';
}
