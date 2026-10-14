import { Injectable } from '@angular/core';
import { Category, CategoryRequest } from '../models/category.model';
import { CrudService } from './crud.service';

@Injectable({ providedIn: 'root' })
export class CategoriesService extends CrudService<Category, CategoryRequest> {
  constructor() {
    super('categories');
  }
}
