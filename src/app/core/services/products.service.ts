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

  /** Guarda el nuevo orden de los productos de una categoria. */
  reorder(ids: number[]): Observable<void> {
    return this.http.put<void>(`${this.url}/order`, { ids });
  }

  /** Sube o sustituye la foto del producto (JPG, PNG o WEBP de hasta 2 MB). */
  uploadImage(id: number, file: File): Observable<Product> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<Product>(`${this.url}/${id}/image`, body);
  }

  removeImage(id: number): Observable<Product> {
    return this.http.delete<Product>(`${this.url}/${id}/image`);
  }

  setActive(id: number, active: boolean): Observable<Product> {
    return this.http.patch<Product>(`${this.url}/${id}/active`, { active });
  }
}
