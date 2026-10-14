import { HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Order, OrderFilters, OrderRequest, OrderStatus } from '../models/order.model';
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
    if (filters.storeId) params = params.set('storeId', filters.storeId);
    if (filters.from) params = params.set('from', filters.from);
    if (filters.to) params = params.set('to', filters.to);
    return this.http.get<Order[]>(this.url, { params });
  }

  changeStatus(id: number, status: OrderStatus): Observable<Order> {
    return this.http.patch<Order>(`${this.url}/${id}/status`, { status });
  }
}
