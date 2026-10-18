import { Injectable } from '@angular/core';
import { Ingredient, IngredientRequest } from '../models/ingredient.model';
import { CrudService } from './crud.service';

@Injectable({ providedIn: 'root' })
export class IngredientsService extends CrudService<Ingredient, IngredientRequest> {
  constructor() {
    super('ingredients');
  }
}
