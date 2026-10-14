import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Category } from '../models/category.model';
import { Order } from '../models/order.model';
import { PublicProduct } from '../models/product.model';
import { Store, TimeSlot } from '../models/store.model';

export interface PublicOrderRequest {
  storeId: number;
  pickupDate: string;
  pickupTime: string;
  customer: { name: string; email: string; phone: string | null; companyId: null };
  notes: string | null;
  lines: { productId: number; quantity: number }[];
}

/** Endpoints sin login usados por la pantalla publica de Nuevo pedido. */
@Injectable({ providedIn: 'root' })
export class PublicApiService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/public`;

  stores(): Observable<Store[]> {
    return this.http.get<Store[]>(`${this.url}/stores`);
  }

  slots(storeId: number, date: string): Observable<TimeSlot[]> {
    return this.http.get<TimeSlot[]>(`${this.url}/stores/${storeId}/slots`, { params: { date } });
  }

  categories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${this.url}/categories`);
  }

  products(): Observable<PublicProduct[]> {
    return this.http.get<PublicProduct[]>(`${this.url}/products`);
  }

  createOrder(body: PublicOrderRequest): Observable<Order> {
    return this.http.post<Order>(`${this.url}/orders`, body);
  }
}
