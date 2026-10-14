import { CurrencyPipe, PercentPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Category } from '../../../core/models/category.model';
import { Ingredient } from '../../../core/models/ingredient.model';
import { Product } from '../../../core/models/product.model';
import { AuthService } from '../../../core/services/auth.service';
import { CategoriesService } from '../../../core/services/categories.service';
import { IngredientsService } from '../../../core/services/ingredients.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ProductsService } from '../../../core/services/products.service';
import { apiErrorMessage } from '../../../core/utils/api-error';

/** Una linea de la receta: ingrediente y cantidad. */
type RecipeLineForm = FormGroup<{ ingredientId: FormControl<number>; quantity: FormControl<number> }>;

@Component({
  imports: [CurrencyPipe, PercentPipe, FormsModule, ReactiveFormsModule],
  selector: 'app-products-page',
  templateUrl: './products-page.component.html',
  styles: '.recipe-row { display: grid; grid-template-columns: 1fr 7rem auto; gap: 0.5rem; margin-bottom: 0.5rem; }',
})
export class ProductsPageComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly productsService = inject(ProductsService);
  private readonly categoriesService = inject(CategoriesService);
  private readonly ingredientsService = inject(IngredientsService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);

  readonly products = signal<Product[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly ingredients = signal<Ingredient[]>([]);
  readonly selectedId = signal<number | null>(null);
  readonly selected = computed(() => this.products().find((p) => p.id === this.selectedId()) ?? null);

  filters = { search: '', categoryId: null as number | null, active: null as boolean | null };

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    categoryId: [0, [Validators.required, Validators.min(1)]],
    price: [0, [Validators.required, Validators.min(0)]],
    active: [true],
    description: [''],
    ingredients: this.fb.array<RecipeLineForm>([]),
  });

  /** Coste estimado de la receta segun el coste actual de cada ingrediente. */
  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  readonly recipeCost = computed(() =>
    (this.formValue().ingredients ?? []).reduce((sum, line) => {
      const ingredient = this.ingredients().find((i) => i.id === Number(line?.ingredientId));
      return sum + (ingredient ? ingredient.unitCost * Number(line?.quantity ?? 0) : 0);
    }, 0),
  );
  readonly margin = computed(() => {
    const price = Number(this.formValue().price ?? 0);
    return price > 0 ? (price - this.recipeCost()) / price : 0;
  });

  get recipe() {
    return this.form.controls.ingredients;
  }

  ngOnInit(): void {
    if (!this.auth.canEdit()) this.form.disable();
    this.categoriesService.list().subscribe((categories) => this.categories.set(categories));
    this.ingredientsService.list().subscribe((ingredients) => this.ingredients.set(ingredients));
    this.load();
  }

  load(): void {
    this.productsService.search(this.filters.search, this.filters.categoryId, this.filters.active).subscribe({
      next: (products) => this.products.set(products),
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  private recipeLine(ingredientId = 0, quantity = 0): RecipeLineForm {
    return this.fb.nonNullable.group({
      ingredientId: [ingredientId, [Validators.required, Validators.min(1)]],
      quantity: [quantity, [Validators.required, Validators.min(0.001)]],
    });
  }

  addRecipeLine(): void {
    this.recipe.push(this.recipeLine());
  }

  removeRecipeLine(index: number): void {
    this.recipe.removeAt(index);
  }

  select(product: Product): void {
    this.selectedId.set(product.id);
    this.recipe.clear();
    product.ingredients.forEach((line) => this.recipe.push(this.recipeLine(line.ingredientId, line.quantity)));
    this.form.patchValue({
      name: product.name,
      categoryId: product.categoryId,
      price: product.price,
      active: product.active,
      description: product.description ?? '',
    });
    if (!this.auth.canEdit()) this.form.disable();
  }

  startCreate(): void {
    this.selectedId.set(null);
    this.recipe.clear();
    this.form.reset({ name: '', categoryId: this.categories()[0]?.id ?? 0, price: 0, active: true, description: '' });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const body = {
      ...value,
      description: value.description || null,
      ingredients: value.ingredients.map((line) => ({ ingredientId: Number(line.ingredientId), quantity: line.quantity })),
    };
    const id = this.selectedId();
    const request = id ? this.productsService.update(id, body) : this.productsService.create(body);
    request.subscribe({
      next: (saved) => {
        this.notify.success(`Producto "${saved.name}" guardado`);
        this.selectedId.set(saved.id);
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  toggleActive(product: Product): void {
    this.productsService.setActive(product.id, !product.active).subscribe({
      next: (updated) => {
        this.products.update((list) => list.map((p) => (p.id === updated.id ? updated : p)));
        this.notify.success(`${updated.name} ${updated.active ? 'activado' : 'desactivado'}`);
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  remove(): void {
    const product = this.selected();
    if (!product || !confirm(`¿Eliminar el producto "${product.name}"?`)) return;
    this.productsService.remove(product.id).subscribe({
      next: () => {
        this.notify.success('Producto eliminado');
        this.startCreate();
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }
}
