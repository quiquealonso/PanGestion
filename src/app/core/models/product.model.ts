export interface ProductIngredient {
  ingredientId: number;
  name: string;
  unit: string;
  quantity: number;
}

export interface Product {
  id: number;
  name: string;
  description: string | null;
  price: number;
  active: boolean;
  categoryId: number;
  categoryName: string;
  categoryColor: string;
  /** Coste de ingredientes de una unidad. */
  cost: number;
  ingredients: ProductIngredient[];
}

export interface ProductRequest {
  name: string;
  description: string | null;
  price: number;
  active: boolean;
  categoryId: number;
  ingredients: { ingredientId: number; quantity: number }[];
}

/** Producto tal y como lo ve el cliente en la parte publica. */
export interface PublicProduct {
  id: number;
  name: string;
  description: string | null;
  price: number;
  categoryId: number;
  categoryName: string;
  categoryColor: string;
  allergens: string[];
}
