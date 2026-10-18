import { DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { WeeklySales } from '../../core/models/dashboard.model';
import { DashboardService } from '../../core/services/dashboard.service';
import { addDays, toIsoDate } from '../../core/utils/dates';

const DAY_NAMES = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

/** Geometria del grafico (unidades del viewBox del SVG). */
const W = 640;
const H = 250;
const M = { top: 12, right: 8, bottom: 30, left: 34 };
const PLOT_W = W - M.left - M.right;
const PLOT_H = H - M.top - M.bottom;
const GROUP_W = PLOT_W / 7;
const BAR_W = 22;
const GAP = 2;

/**
 * Barras agrupadas: unidades vendidas cada dia de una semana frente a la semana anterior.
 * Se puede navegar de semana en semana; al pasar el raton por un dia se ve el detalle.
 */
@Component({
  selector: 'app-weekly-sales-chart',
  imports: [DatePipe, DecimalPipe, PercentPipe],
  templateUrl: './weekly-sales-chart.component.html',
  styleUrl: './weekly-sales-chart.component.scss',
})
export class WeeklySalesChartComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);

  readonly geometry = { W, H, M, PLOT_W, PLOT_H, GROUP_W };
  readonly today = toIsoDate();
  /** Cualquier dia de la semana que se esta viendo. */
  readonly date = signal(this.today);
  readonly data = signal<WeeklySales | null>(null);
  readonly hovered = signal<number | null>(null);
  readonly showTable = signal(false);

  readonly isCurrentWeek = computed(() => {
    const start = this.data()?.weekStart;
    return !!start && this.today >= start && this.today <= addDays(6, new Date(start + 'T00:00'));
  });

  readonly days = computed(() => {
    const d = this.data();
    if (!d) return [];
    const start = new Date(d.weekStart + 'T00:00');
    return DAY_NAMES.map((name, i) => ({
      name,
      date: addDays(i, start),
      current: d.current[i],
      previous: d.previous[i],
    }));
  });

  readonly totalCurrent = computed(() => this.data()?.current.reduce((a, b) => a + b, 0) ?? 0);
  readonly totalPrevious = computed(() => this.data()?.previous.reduce((a, b) => a + b, 0) ?? 0);
  /** Variacion respecto a la semana anterior (null si la anterior fue 0). */
  readonly change = computed(() => {
    const prev = this.totalPrevious();
    return prev === 0 ? null : (this.totalCurrent() - prev) / prev;
  });

  /** Maximo del eje Y redondeado a un valor "bonito" y sus 4 marcas. */
  readonly scale = computed(() => {
    const d = this.data();
    const max = d ? Math.max(1, ...d.current, ...d.previous) : 1;
    const raw = max / 4;
    const magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
    const step = [1, 2, 5, 10].map((f) => f * magnitude).find((s) => s >= raw) ?? raw;
    const top = Math.max(step * 4, 4);
    return { top, ticks: [0, 1, 2, 3, 4].map((i) => (top / 4) * i) };
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.dashboardService.weeklySales(this.date()).subscribe((data) => this.data.set(data));
  }

  moveWeek(weeks: number): void {
    this.date.set(addDays(weeks * 7, new Date(this.date() + 'T00:00')));
    this.hovered.set(null);
    this.load();
  }

  goToThisWeek(): void {
    this.date.set(this.today);
    this.load();
  }

  // ----- Geometria -----

  y(value: number): number {
    return M.top + PLOT_H - (value / this.scale().top) * PLOT_H;
  }

  groupX(index: number): number {
    return M.left + GROUP_W * index;
  }

  /** x de cada barra: la semana anterior a la izquierda y la actual a la derecha, centradas en el dia. */
  barX(index: number, series: 'previous' | 'current'): number {
    const center = this.groupX(index) + GROUP_W / 2;
    return series === 'previous' ? center - BAR_W - GAP / 2 : center + GAP / 2;
  }

  /** Barra con las esquinas de arriba redondeadas (4px) y apoyada en la linea base. */
  barPath(index: number, series: 'previous' | 'current', value: number): string {
    if (value <= 0) return '';
    const x = this.barX(index, series);
    const top = this.y(value);
    const bottom = M.top + PLOT_H;
    const r = Math.min(4, (bottom - top) / 2, BAR_W / 2);
    return `M${x},${bottom} V${top + r} Q${x},${top} ${x + r},${top} H${x + BAR_W - r} Q${x + BAR_W},${top} ${x + BAR_W},${top + r} V${bottom} Z`;
  }

  /** Posicion horizontal del tooltip, en % del ancho del grafico. */
  tooltipLeft(index: number): number {
    return ((this.groupX(index) + GROUP_W / 2) / W) * 100;
  }
}
