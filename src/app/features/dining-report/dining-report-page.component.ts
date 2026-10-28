import { CurrencyPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../environments/environment';
import { DiningReport } from '../../core/models/dining-report.model';
import { STATION_LABELS } from '../../core/models/product.model';
import { NotificationService } from '../../core/services/notification.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { BarDatum, SimpleBarChartComponent } from '../../shared/simple-bar-chart.component';

type Preset = 'today' | 'week' | 'month' | 'thisMonth';

const euros = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' });
const eurosShort = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 });
const dayFormat = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short' });

/**
 * Informes de sala: lo cobrado, las mesas y los comensales de un periodo, cuando se llena el local,
 * lo que mas se vende, como va cada camarero y cada mesa, y cuanto tardan cocina y barra.
 */
@Component({
  imports: [CurrencyPipe, FormsModule, SimpleBarChartComponent],
  selector: 'app-dining-report-page',
  templateUrl: './dining-report-page.component.html',
  styleUrl: './dining-report-page.component.scss',
})
export class DiningReportPageComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly notify = inject(NotificationService);

  readonly stationLabels = STATION_LABELS;
  readonly report = signal<DiningReport | null>(null);
  readonly preset = signal<Preset | null>('week');
  from = isoDate(addDays(new Date(), -6));
  to = isoDate(new Date());

  readonly byDay = computed<BarDatum[]>(() =>
    (this.report()?.byDay ?? []).map((d) => ({
      label: dayFormat.format(new Date(d.date + 'T00:00')),
      value: d.revenue,
      tooltip: `${euros.format(d.revenue)} · ${d.tables} mesas`,
    })),
  );
  readonly byHour = computed<BarDatum[]>(() =>
    (this.report()?.byHour ?? []).map((h) => ({
      label: `${h.hour}h`,
      value: h.tables,
      tooltip: `${h.tables} mesas · ${euros.format(h.revenue)}`,
    })),
  );
  readonly euroTicks = (v: number) => eurosShort.format(v);
  readonly countTicks = (v: number) => String(Math.round(v * 10) / 10);
  /** Barra de cada producto, proporcional al mas vendido */
  readonly topMax = computed(() => Math.max(1, ...(this.report()?.topProducts ?? []).map((p) => p.quantity)));

  ngOnInit(): void {
    this.load();
  }

  setPreset(preset: Preset): void {
    const today = new Date();
    const from =
      preset === 'today' ? today
      : preset === 'week' ? addDays(today, -6)
      : preset === 'month' ? addDays(today, -29)
      : new Date(today.getFullYear(), today.getMonth(), 1);
    this.from = isoDate(from);
    this.to = isoDate(today);
    this.preset.set(preset);
    this.load();
  }

  changeDates(): void {
    this.preset.set(null);
    this.load();
  }

  load(): void {
    if (!this.from || !this.to) return;
    this.http
      .get<DiningReport>(`${environment.apiBaseUrl}/reports/dining`, { params: { from: this.from, to: this.to } })
      .subscribe({
        next: (report) => this.report.set(report),
        error: (err) => this.notify.error(apiErrorMessage(err)),
      });
  }
}

function addDays(date: Date, days: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}

function isoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
