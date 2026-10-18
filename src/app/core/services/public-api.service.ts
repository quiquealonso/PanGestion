import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Category } from '../models/category.model';
import { Company, TimeSlot } from '../models/company.model';
import { Order } from '../models/order.model';
import { PublicProduct } from '../models/product.model';

export interface PublicOrderRequest {
  companyId: number;
  pickupDate: string;
  pickupTime: string;
  customer: { name: string; email: string; phone: string | null; businessName: null; taxId: null };
  notes: string | null;
  lines: { productId: number; quantity: number }[];
}

/** Endpoints sin login de la web publica: el cliente elige la panaderia y pide a ella. */
@Injectable({ providedIn: 'root' })
export class PublicApiService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/public`;

  /** Panaderias que aceptan pedidos. */
  companies(): Observable<Company[]> {
    return this.http.get<Company[]>(`${this.url}/companies`);
  }

  slots(companyId: number, date: string): Observable<TimeSlot[]> {
    return this.http.get<TimeSlot[]>(`${this.url}/companies/${companyId}/slots`, { params: { date } });
  }

  categories(companyId: number): Observable<Category[]> {
    return this.http.get<Category[]>(`${this.url}/companies/${companyId}/categories`);
  }

  products(companyId: number): Observable<PublicProduct[]> {
    return this.http.get<PublicProduct[]>(`${this.url}/companies/${companyId}/products`);
  }

  createOrder(body: PublicOrderRequest): Observable<Order> {
    return this.http.post<Order>(`${this.url}/orders`, body);
  }
}
