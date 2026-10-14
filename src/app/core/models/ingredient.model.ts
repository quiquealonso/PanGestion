export interface Ingredient {
  id: number;
  name: string;
  unit: string;
  stock: number;
  minStock: number;
  unitCost: number;
  allergen: string | null;
  lowStock: boolean;
}

export type IngredientRequest = Omit<Ingredient, 'id' | 'lowStock'>;
