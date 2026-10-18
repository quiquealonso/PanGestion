/** Ingrediente que forma parte de los productos. */
export interface Ingredient {
  id: number;
  name: string;
  allergen: string | null;
  /** En cuantos productos se usa. */
  productCount: number;
}

export type IngredientRequest = Omit<Ingredient, 'id' | 'productCount'>;
