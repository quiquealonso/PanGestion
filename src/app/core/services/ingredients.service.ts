import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { Extra, Ingredient, IngredientRequest } from '../models/ingredient.model';
import { CrudService } from './crud.service';

@Injectable({ providedIn: 'root' })
export class IngredientsService extends CrudService<Ingredient, IngredientRequest> {
  constructor() {
    super('ingredients');
  }

  /** Ingredientes que se pueden anadir como extra (los que tienen precio de extra). */
  extras(): Observable<Extra[]> {
    return this.list().pipe(
      map((ingredients) =>
        ingredients
          .filter((i) => i.extraPrice !== null)
          .map((i) => ({ id: i.id, name: i.name, allergen: i.allergen, price: i.extraPrice! })),
      ),
    );
  }
}
