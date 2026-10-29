import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { merge } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DiningDashboard } from '../../core/models/dashboard.model';
import { DiningReport } from '../../core/models/dining-report.model';
import { AuthService } from '../../core/services/auth.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { LiveEventsService } from '../../core/services/live-events.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { BarDatum, SimpleBarChartComponent } from '../../shared/simple-bar-chart.component';

/** Respaldo por si se corta la conexion de avisos en tiempo real. */
const REFRESH_MS = 60_000;

const euros = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' });
const eurosShort = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 });
const dayFormat = new Intl.DateTimeFormat('es-ES', { weekday: 'short', day: 'numeric' });

/**
 * Inicio de un bar o restaurante: la sala ahora mismo (mesas, comensales, lo que esta listo y las colas
 * de cocina y barra), la caja de hoy, accesos a cada pantalla y, para quien ve la facturacion, la ultima semana.
 * Se actualiza solo con los avisos en tiempo real.
 */
@Component({
  selector: 'app-dining-home',
  imports: [CurrencyPipe, DatePipe, RouterLink, SimpleBarChartComponent],
  templateUrl: './dining-home.component.html',
  styleUrls: ['./dashboard-page.component.scss', './dining-home.component.scss'],
})
export class DiningHomeComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);
  protected readonly live = inject(LiveEventsService);

  readonly today = new Date();
  readonly data = signal<DiningDashboard | null>(null);
  readonly week = signal<DiningReport | null>(null);
  readonly error = signal<string | null>(null);

  readonly occupancy = computed(() => {
    const d = this.data();
    return d && d.tables ? Math.round((d.occupiedTables / d.tables) * 100) : 0;
  });
  readonly byDay = computed<BarDatum[]>(() =>
    (this.week()?.byDay ?? []).map((d) => ({
      label: dayFormat.format(new Date(d.date + 'T00:00')),
      value: d.revenue,
      tooltip: `${euros.format(d.revenue)} · ${d.tables} mesas`,
    })),
  );
  readonly euroTicks = (v: number) => eurosShort.format(v);
  readonly topMax = computed(() => Math.max(1, ...(this.week()?.topProducts ?? []).map((p) => p.quantity)));

  ngOnInit(): void {
    this.load();
    if (this.auth.canReport()) this.loadWeek();
    const changes = merge(this.live.on('floor'), this.live.on('kitchen'), this.live.on('orders')).subscribe(() => this.load());
    const timer = setInterval(() => this.load(), REFRESH_MS);
    this.destroyRef.onDestroy(() => {
      changes.unsubscribe();
      clearInterval(timer);
    });
  }

  load(): void {
    this.dashboardService.dining().subscribe({
      next: (data) => {
        this.data.set(data);
        this.error.set(null);
      },
      error: (err) => this.error.set(apiErrorMessage(err)),
    });
  }

  /** Ultimos 7 dias (el informe de sala, sin fechas). */
  private loadWeek(): void {
    this.http.get<DiningReport>(`${environment.apiBaseUrl}/reports/dining`).subscribe({
      next: (report) => this.week.set(report),
    });
  }
}
