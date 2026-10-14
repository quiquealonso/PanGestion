export interface Category {
  id: number;
  name: string;
  color: string;
  description: string | null;
  productCount: number;
}

export type CategoryRequest = Omit<Category, 'id' | 'productCount'>;
