import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Dashboard, WeeklySales } from '../models/dashboard.model';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);

  summary(): Observable<Dashboard> {
    return this.http.get<Dashboard>(`${environment.apiBaseUrl}/dashboard`);
  }

  /** Ventas por dia de la semana que contiene "date" (yyyy-MM-dd) y de la anterior. */
  weeklySales(date: string): Observable<WeeklySales> {
    return this.http.get<WeeklySales>(`${environment.apiBaseUrl}/dashboard/weekly-sales`, { params: { date } });
  }
}
