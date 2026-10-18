/** Ingrediente dentro de un producto. */
export interface ProductIngredient {
  id: number;
  name: string;
  allergen: string | null;
}

export interface Product {
  id: number;
  name: string;
  /** Nombre corto para tickets y produccion. */
  abbreviation: string | null;
  description: string | null;
  price: number;
  active: boolean;
  /** Color propio; null = usa el de la categoria. */
  color: string | null;
  /** Color que se pinta (propio o el de la categoria). */
  displayColor: string;
  /** Posicion dentro de su categoria. */
  sortOrder: number;
  categoryId: number;
  categoryName: string;
  categoryColor: string;
  /** URL de la foto (/uploads/...); null = sin foto. */
  imageUrl: string | null;
  ingredients: ProductIngredient[];
}

export interface ProductRequest {
  name: string;
  abbreviation: string | null;
  description: string | null;
  color: string | null;
  price: number;
  active: boolean;
  categoryId: number;
  ingredientIds: number[];
}

/** Producto tal y como lo ve el cliente en la parte publica. */
export interface PublicProduct {
  id: number;
  name: string;
  description: string | null;
  price: number;
  color: string;
  imageUrl: string | null;
  categoryId: number;
  categoryName: string;
  categoryColor: string;
  ingredients: string[];
  allergens: string[];
}
