import { HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Product, ProductRequest } from '../models/product.model';
import { CrudService } from './crud.service';

@Injectable({ providedIn: 'root' })
export class ProductsService extends CrudService<Product, ProductRequest> {
  constructor() {
    super('products');
  }

  search(search: string, categoryId: number | null, active: boolean | null): Observable<Product[]> {
    let params = new HttpParams();
    if (search) params = params.set('search', search);
    if (categoryId) params = params.set('categoryId', categoryId);
    if (active !== null) params = params.set('active', active);
    return this.http.get<Product[]>(this.url, { params });
  }

  setActive(id: number, active: boolean): Observable<Product> {
    return this.http.patch<Product>(`${this.url}/${id}/active`, { active });
  }
}
