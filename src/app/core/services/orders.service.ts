import { HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Order, OrderFilters, OrderRequest, OrderStatus, ProductionRow } from '../models/order.model';
import { CrudService } from './crud.service';

@Injectable({ providedIn: 'root' })
export class OrdersService extends CrudService<Order, OrderRequest> {
  constructor() {
    super('orders');
  }

  search(filters: OrderFilters): Observable<Order[]> {
    let params = new HttpParams();
    if (filters.search) params = params.set('search', filters.search);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.from) params = params.set('from', filters.from);
    if (filters.to) params = params.set('to', filters.to);
    return this.http.get<Order[]>(this.url, { params });
  }

  /** Hoja de produccion de un dia. pending = sin los ya entregados. */
  production(date: string, pending = true): Observable<ProductionRow[]> {
    return this.http.get<ProductionRow[]>(`${this.url}/production`, { params: { date, pending } });
  }

  /** Pasa a "En proceso" todos los pedidos pendientes que se recogen ese dia. */
  startDay(date: string): Observable<{ updated: number }> {
    return this.http.post<{ updated: number }>(`${this.url}/start-day`, null, { params: { date } });
  }

  changeStatus(id: number, status: OrderStatus): Observable<Order> {
    return this.http.patch<Order>(`${this.url}/${id}/status`, { status });
  }
}
