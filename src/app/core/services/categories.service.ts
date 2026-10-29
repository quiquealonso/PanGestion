import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Category, CategoryRequest } from '../models/category.model';
import { CrudService } from './crud.service';

@Injectable({ providedIn: 'root' })
export class CategoriesService extends CrudService<Category, CategoryRequest> {
  constructor() {
    super('categories');
  }

  /** Guarda el nuevo orden (ids en la posicion deseada). */
  reorder(ids: number[]): Observable<Category[]> {
    return this.http.put<Category[]>(`${this.url}/order`, { ids });
  }

  /** Guarda el orden de la pantalla de cocina (ids de arriba abajo). No cambia el del catalogo. */
  reorderKitchen(ids: number[]): Observable<Category[]> {
    return this.http.put<Category[]>(`${this.url}/kitchen-order`, { ids });
  }
}
