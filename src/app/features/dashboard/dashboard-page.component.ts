import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Dashboard } from '../../core/models/dashboard.model';
import { AuthService } from '../../core/services/auth.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { OrderStatusPipe } from '../../shared/order-status.pipe';

@Component({
  imports: [CurrencyPipe, DatePipe, RouterLink, OrderStatusPipe],
  selector: 'app-dashboard-page',
  styleUrl: './dashboard-page.component.scss',
  templateUrl: './dashboard-page.component.html',
})
export class DashboardPageComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  protected readonly auth = inject(AuthService);

  readonly data = signal<Dashboard | null>(null);
  readonly error = signal<string | null>(null);
  readonly today = new Date();

  ngOnInit(): void {
    this.dashboardService.summary().subscribe({
      next: (data) => this.data.set(data),
      error: (err) => this.error.set(apiErrorMessage(err)),
    });
  }

  /** Ancho relativo de la barra de cada producto en el ranking. */
  barWidth(units: number): number {
    const max = this.data()?.topProducts[0]?.units ?? 1;
    return Math.round((units / max) * 100);
  }
}
