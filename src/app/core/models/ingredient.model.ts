/** Ingrediente que forma parte de los productos. */
export interface Ingredient {
  id: number;
  name: string;
  allergen: string | null;
  /** Precio si se anade como extra ("con huevo"); null = no se ofrece como extra, 0 = gratis. */
  extraPrice: number | null;
  /** En cuantos productos se usa. */
  productCount: number;
}

export type IngredientRequest = Omit<Ingredient, 'id' | 'productCount'>;

/** Extra que se puede anadir a un producto. */
export interface Extra {
  id: number;
  name: string;
  allergen: string | null;
  price: number;
}

/** Extra de una linea ya pedida: nombre y precio del momento. */
export interface LineExtra {
  name: string;
  price: number;
}
