import { HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Customer, CustomerRequest } from '../models/customer.model';
import { Order } from '../models/order.model';
import { CrudService } from './crud.service';

@Injectable({ providedIn: 'root' })
export class CustomersService extends CrudService<Customer, CustomerRequest> {
  constructor() {
    super('customers');
  }

  search(search: string): Observable<Customer[]> {
    const params = search ? new HttpParams().set('search', search) : undefined;
    return this.http.get<Customer[]>(this.url, { params });
  }

  orders(id: number): Observable<Order[]> {
    return this.http.get<Order[]>(`${this.url}/${id}/orders`);
  }
}
