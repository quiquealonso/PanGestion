import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Ingredient, IngredientRequest } from '../models/ingredient.model';
import { CrudService } from './crud.service';

@Injectable({ providedIn: 'root' })
export class IngredientsService extends CrudService<Ingredient, IngredientRequest> {
  constructor() {
    super('ingredients');
  }

  /** Entrada (positiva) o salida (negativa) de stock. */
  adjustStock(id: number, amount: number): Observable<Ingredient> {
    return this.http.patch<Ingredient>(`${this.url}/${id}/stock`, { amount });
  }
}
