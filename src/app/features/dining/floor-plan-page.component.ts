import { Component, ElementRef, HostListener, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DiningArea, DiningAreaRequest, DiningTable, TableShape } from '../../core/models/dining.model';
import { AuthService } from '../../core/services/auth.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { DiningService } from '../../core/services/dining.service';
import { NotificationService } from '../../core/services/notification.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { DiningTableComponent } from '../../shared/dining-table.component';
import { ModalComponent } from '../../shared/modal/modal.component';

/** Tipos de mesa que se pueden anadir con un clic. */
const PRESETS = [
  { label: 'Redonda', shape: 'ROUND' as TableShape, width: 2, height: 2, seats: 4 },
  { label: 'Cuadrada', shape: 'RECT' as TableShape, width: 2, height: 2, seats: 4 },
  { label: 'Alargada', shape: 'RECT' as TableShape, width: 4, height: 2, seats: 6 },
  { label: 'Taburete', shape: 'ROUND' as TableShape, width: 1, height: 1, seats: 1 },
];

/** Nombres tipicos para una zona nueva. */
const AREA_NAMES = ['Interior', 'Terraza', 'Salón', 'Barra', 'Comedor', 'Reservado'];

/** Arrastre en curso: mover la mesa o cambiar su tamano desde la esquina. */
interface Gesture {
  mode: 'move' | 'resize';
  origin: DiningTable;
  startX: number;
  startY: number;
  /** Pixeles que mide una celda en pantalla */
  cell: number;
  moved: boolean;
}

/**
 * Plano de la sala de un bar o restaurante: zonas en pestanas y mesas que se colocan sobre una cuadricula.
 * Las mesas se arrastran, se redimensionan desde la esquina y se editan en el panel lateral.
 * Cada cambio se guarda al soltar; el servidor valida que la mesa quepa y no pise a otra.
 */
@Component({
  imports: [FormsModule, ModalComponent, DiningTableComponent],
  selector: 'app-floor-plan-page',
  templateUrl: './floor-plan-page.component.html',
  styleUrl: './floor-plan-page.component.scss',
})
export class FloorPlanPageComponent implements OnInit {
  private readonly dining = inject(DiningService);
  private readonly notify = inject(NotificationService);
  private readonly confirmService = inject(ConfirmService);
  protected readonly auth = inject(AuthService);

  private readonly canvas = viewChild<ElementRef<HTMLElement>>('canvas');

  readonly presets = PRESETS;
  readonly areaNames = AREA_NAMES;
  readonly loading = signal(true);
  readonly areas = signal<DiningArea[]>([]);
  readonly activeId = signal<number | null>(null);
  readonly active = computed(() => this.areas().find((a) => a.id === this.activeId()) ?? this.areas()[0] ?? null);
  readonly selectedId = signal<number | null>(null);
  readonly selected = computed(() => this.active()?.tables.find((t) => t.id === this.selectedId()) ?? null);
  /** Copia de la mesa mientras se arrastra (aun sin guardar) */
  readonly draft = signal<DiningTable | null>(null);
  readonly draftInvalid = computed(() => {
    const draft = this.draft();
    const area = this.active();
    return !!draft && !!area && !this.canPlace(area, draft);
  });
  readonly totals = computed(() => ({
    tables: this.areas().reduce((sum, a) => sum + a.tables.length, 0),
    seats: this.areas().reduce((sum, a) => sum + a.seats, 0),
  }));

  /** Dialogo de zona: nueva o editar la activa */
  readonly areaDialog = signal<'new' | 'edit' | null>(null);
  areaForm: DiningAreaRequest = { name: '', width: 24, height: 14 };
  nameDraft = '';

  private gesture: Gesture | null = null;
  /** Mesas con una peticion de guardado en curso, y el ultimo cambio que espera a que acabe */
  private readonly saving = new Set<number>();
  private readonly pendingSaves = new Map<number, DiningTable>();

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.dining.areas().subscribe({
      next: (areas) => {
        this.areas.set(areas);
        this.nameDraft = this.selected()?.name ?? '';
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.notify.error(apiErrorMessage(err));
      },
    });
  }

  selectArea(area: DiningArea): void {
    this.activeId.set(area.id);
    this.select(null);
  }

  select(table: DiningTable | null): void {
    this.selectedId.set(table?.id ?? null);
    this.nameDraft = table?.name ?? '';
  }

  /** La mesa tal como se ve: la copia en curso si se esta arrastrando. */
  view(table: DiningTable): DiningTable {
    const draft = this.draft();
    return draft && draft.id === table.id ? draft : table;
  }

  // ----- Zonas -----

  openNewArea(): void {
    const used = new Set(this.areas().map((a) => a.name.toLowerCase()));
    const name = AREA_NAMES.find((n) => !used.has(n.toLowerCase())) ?? '';
    this.areaForm = { name, width: 24, height: 14 };
    this.areaDialog.set('new');
  }

  openEditArea(): void {
    const area = this.active();
    if (!area) return;
    this.areaForm = { name: area.name, width: area.width, height: area.height };
    this.areaDialog.set('edit');
  }

  saveArea(): void {
    const area = this.active();
    const editing = this.areaDialog() === 'edit' && area;
    const body = { ...this.areaForm, name: this.areaForm.name.trim() };
    const request = editing ? this.dining.updateArea(area.id, body) : this.dining.createArea(body);
    request.subscribe({
      next: (saved) => {
        this.areas.update((list) => (editing ? list.map((a) => (a.id === saved.id ? saved : a)) : [...list, saved]));
        this.activeId.set(saved.id);
        this.areaDialog.set(null);
        this.notify.success(`Zona "${saved.name}" guardada`);
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  async removeArea(): Promise<void> {
    const area = this.active();
    if (!area) return;
    const accepted = await this.confirmService.ask({
      title: 'Eliminar zona',
      message: `¿Eliminar "${area.name}" y sus ${area.tables.length} mesas? Esta acción no se puede deshacer.`,
    });
    if (!accepted) return;
    this.dining.removeArea(area.id).subscribe({
      next: () => {
        this.areas.update((list) => list.filter((a) => a.id !== area.id));
        this.activeId.set(null);
        this.areaDialog.set(null);
        this.select(null);
        this.notify.success('Zona eliminada');
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  // ----- Mesas -----

  addTable(preset: (typeof PRESETS)[number]): void {
    const area = this.active();
    if (!area) return;
    const spot = this.freeSpot(area, preset.width, preset.height);
    if (!spot) {
      this.notify.error('No queda sitio libre en la zona: amplíala o mueve alguna mesa');
      return;
    }
    const body = { name: this.nextName(), seats: preset.seats, shape: preset.shape, ...spot, width: preset.width, height: preset.height };
    this.dining.createTable(area.id, body).subscribe({
      next: (table) => {
        this.replaceTable(table, true);
        this.select(table);
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  duplicate(): void {
    const table = this.selected();
    const area = this.active();
    if (!table || !area) return;
    const spot = this.freeSpot(area, table.width, table.height);
    if (!spot) {
      this.notify.error('No queda sitio libre para otra mesa igual');
      return;
    }
    const { id, areaId, ...body } = table;
    this.dining.createTable(area.id, { ...body, ...spot, name: this.nextName() }).subscribe({
      next: (copy) => {
        this.replaceTable(copy, true);
        this.select(copy);
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  /** Cambia algo de la mesa seleccionada y lo guarda (si cabe y no pisa a otra). */
  patch(changes: Partial<DiningTable>): void {
    const table = this.selected();
    const area = this.active();
    if (!table || !area) return;
    const next = { ...table, ...changes };
    if (!this.canPlace(area, next)) {
      this.notify.error('No cabe ahí: se saldría de la zona o pisaría otra mesa');
      return;
    }
    this.save(next);
  }

  /** Botones - y +: suma sobre el valor actual (no sobre el ya pintado), dentro de sus limites. */
  step(field: 'seats' | 'width' | 'height', delta: number): void {
    const table = this.selected();
    if (!table) return;
    const value = table[field] + delta;
    const max = field === 'seats' ? 30 : 12;
    if (value >= 1 && value <= max) this.patch({ [field]: value });
  }

  rename(): void {
    const name = this.nameDraft.trim();
    if (name && name !== this.selected()?.name) this.patch({ name });
  }

  /** Gira la mesa 90 grados (intercambia ancho y alto). */
  rotate(): void {
    const table = this.selected();
    if (table) this.patch({ width: table.height, height: table.width });
  }

  async removeTable(): Promise<void> {
    const table = this.selected();
    if (!table) return;
    const accepted = await this.confirmService.ask({ title: 'Eliminar mesa', message: `¿Eliminar la mesa ${table.name}?` });
    if (!accepted) return;
    this.dining.removeTable(table.id).subscribe({
      next: () => {
        this.areas.update((list) =>
          list.map((a) => (a.id === table.areaId ? withTables(a, a.tables.filter((t) => t.id !== table.id)) : a)),
        );
        this.select(null);
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  /**
   * Guarda la mesa. El cambio se ve al momento; al servidor se envia una peticion por mesa cada vez
   * (si llegan varios cambios seguidos, al acabar se envia solo el ultimo), asi no se pisan entre si.
   * Si el servidor rechaza un cambio, se recarga el plano para volver a lo guardado.
   */
  private save(table: DiningTable): void {
    this.replaceTable(table);
    this.draft.set(null);
    if (this.saving.has(table.id)) {
      this.pendingSaves.set(table.id, table);
    } else {
      this.send(table);
    }
  }

  private send(table: DiningTable): void {
    const { id, areaId, ...body } = table;
    this.saving.add(id);
    this.dining.updateTable(id, body).subscribe({
      next: () => this.sendPending(id),
      error: (err) => {
        this.saving.delete(id);
        this.pendingSaves.delete(id);
        this.notify.error(apiErrorMessage(err));
        this.load();
      },
    });
  }

  private sendPending(id: number): void {
    this.saving.delete(id);
    const next = this.pendingSaves.get(id);
    if (next) {
      this.pendingSaves.delete(id);
      this.send(next);
    }
  }

  private replaceTable(table: DiningTable, added = false): void {
    this.areas.update((list) =>
      list.map((a) => {
        if (a.id !== table.areaId) return a;
        const tables = added ? [...a.tables, table] : a.tables.map((t) => (t.id === table.id ? table : t));
        return withTables(a, tables);
      }),
    );
  }

  // ----- Arrastrar y redimensionar -----

  startGesture(event: PointerEvent, table: DiningTable, mode: Gesture['mode']): void {
    event.stopPropagation();
    this.select(table);
    const area = this.active();
    const canvas = this.canvas()?.nativeElement;
    if (!this.auth.canEdit() || !area || !canvas || event.button !== 0) return;
    event.preventDefault();
    this.gesture = {
      mode,
      origin: table,
      startX: event.clientX,
      startY: event.clientY,
      cell: canvas.getBoundingClientRect().width / area.width,
      moved: false,
    };
  }

  @HostListener('window:pointermove', ['$event'])
  onPointerMove(event: PointerEvent): void {
    const gesture = this.gesture;
    const area = this.active();
    if (!gesture || !area) return;
    const dx = Math.round((event.clientX - gesture.startX) / gesture.cell);
    const dy = Math.round((event.clientY - gesture.startY) / gesture.cell);
    if (!dx && !dy && !gesture.moved) return;
    gesture.moved = true;
    const o = gesture.origin;
    this.draft.set(
      gesture.mode === 'move'
        ? { ...o, x: clamp(o.x + dx, 0, area.width - o.width), y: clamp(o.y + dy, 0, area.height - o.height) }
        : {
            ...o,
            width: clamp(o.width + dx, 1, Math.min(12, area.width - o.x)),
            height: clamp(o.height + dy, 1, Math.min(12, area.height - o.y)),
          },
    );
  }

  @HostListener('window:pointerup')
  onPointerUp(): void {
    const draft = this.draft();
    this.gesture = null;
    if (!draft) return;
    const original = this.active()?.tables.find((t) => t.id === draft.id);
    const unchanged = original && ['x', 'y', 'width', 'height'].every((k) => original[k as keyof DiningTable] === draft[k as keyof DiningTable]);
    if (unchanged || this.draftInvalid()) {
      if (this.draftInvalid()) this.notify.error('Ahí no cabe: pisaría otra mesa');
      this.draft.set(null);
      return;
    }
    this.save(draft);
  }

  /** Teclado en el plano: flechas mueven, Supr elimina, Esc deselecciona. */
  onKey(event: KeyboardEvent): void {
    const table = this.selected();
    if (!table || !this.auth.canEdit()) return;
    const moves: Record<string, [number, number]> = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };
    const move = moves[event.key];
    if (move) {
      event.preventDefault();
      const area = this.active()!;
      const next = { ...table, x: table.x + move[0], y: table.y + move[1] };
      if (this.canPlace(area, next)) this.save(next);
    } else if (event.key === 'Delete' || event.key === 'Backspace') {
      event.preventDefault();
      this.removeTable();
    } else if (event.key === 'Escape') {
      this.select(null);
    }
  }

  private canPlace(area: DiningArea, table: DiningTable): boolean {
    const fits = table.x >= 0 && table.y >= 0 && table.x + table.width <= area.width && table.y + table.height <= area.height;
    return fits && !area.tables.some((other) => other.id !== table.id && overlaps(other, table));
  }

  /**
   * Primer hueco libre (de arriba a abajo, de izquierda a derecha) para una mesa de ese tamano.
   * Primero busca uno con una casilla de pasillo alrededor (para las sillas); si no hay, uno pegado.
   */
  private freeSpot(area: DiningArea, width: number, height: number): { x: number; y: number } | null {
    for (const gap of [1, 0]) {
      for (let y = 0; y + height <= area.height; y++) {
        for (let x = 0; x + width <= area.width; x++) {
          const room = { id: -1, x: x - gap, y: y - gap, width: width + 2 * gap, height: height + 2 * gap } as DiningTable;
          const fits = x + width <= area.width && y + height <= area.height;
          if (fits && !area.tables.some((other) => overlaps(other, room))) return { x, y };
        }
      }
    }
    return null;
  }

  /** Menor numero que no usa ninguna mesa de la empresa. */
  private nextName(): string {
    const used = new Set(this.areas().flatMap((a) => a.tables.map((t) => t.name.toLowerCase())));
    let n = 1;
    while (used.has(String(n))) n++;
    return String(n);
  }
}

function overlaps(a: DiningTable, b: DiningTable): boolean {
  return a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height;
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

function withTables(area: DiningArea, tables: DiningTable[]): DiningArea {
  return { ...area, tables, seats: tables.reduce((sum, t) => sum + t.seats, 0) };
}
