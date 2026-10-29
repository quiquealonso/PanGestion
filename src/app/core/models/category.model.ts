import { Station } from './product.model';

export interface Category {
  id: number;
  name: string;
  color: string;
  description: string | null;
  /** Posicion en el catalogo. */
  sortOrder: number;
  productCount: number;
  /** Donde se preparan sus productos (bares y restaurantes); null = lo decide cada producto. */
  station: Station | null;
  /** Posicion en la pantalla de cocina (0 = arriba). */
  kitchenOrder: number;
}

export type CategoryRequest = Omit<Category, 'id' | 'productCount' | 'sortOrder' | 'kitchenOrder'>;
