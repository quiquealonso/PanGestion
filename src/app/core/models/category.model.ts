export interface Category {
  id: number;
  name: string;
  color: string;
  description: string | null;
  /** Posicion en el catalogo. */
  sortOrder: number;
  productCount: number;
}

export type CategoryRequest = Omit<Category, 'id' | 'productCount' | 'sortOrder'>;
