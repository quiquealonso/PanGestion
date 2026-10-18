import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Ingredient } from '../../../core/models/ingredient.model';
import { Product } from '../../../core/models/product.model';
import { AuthService } from '../../../core/services/auth.service';
import { IngredientsService } from '../../../core/services/ingredients.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ProductsService } from '../../../core/services/products.service';
import { apiErrorMessage } from '../../../core/utils/api-error';
import { ModalComponent } from '../../../shared/modal/modal.component';

/** Ingredientes de los que estan hechos los productos (y sus alergenos). */
@Component({
  imports: [FormsModule, ReactiveFormsModule, ModalComponent],
  selector: 'app-ingredients-page',
  templateUrl: './ingredients-page.component.html',
  styles: '.used-in { margin-top: 1.2rem; } .chips { display: flex; flex-wrap: wrap; gap: 0.4rem; margin-top: 0.5rem; }',
})
export class IngredientsPageComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly ingredientsService = inject(IngredientsService);
  private readonly productsService = inject(ProductsService);
  private readonly notify = inject(NotificationService);
  private readonly confirmService = inject(ConfirmService);
  protected readonly auth = inject(AuthService);

  readonly ingredients = signal<Ingredient[]>([]);
  readonly products = signal<Product[]>([]);
  readonly selectedId = signal<number | null>(null);
  readonly dialogOpen = signal(false);
  readonly search = signal('');

  readonly selected = computed(() => this.ingredients().find((i) => i.id === this.selectedId()) ?? null);
  readonly visible = computed(() => {
    const term = this.search().trim().toLowerCase();
    return this.ingredients().filter((i) => !term || i.name.toLowerCase().includes(term));
  });
  readonly withAllergen = computed(() => this.ingredients().filter((i) => i.allergen).length);
  readonly unused = computed(() => this.ingredients().filter((i) => i.productCount === 0).length);
  /** Productos que llevan el ingrediente abierto en el dialogo. */
  readonly usedIn = computed(() => {
    const id = this.selectedId();
    return this.products().filter((p) => p.ingredients.some((i) => i.id === id));
  });

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    allergen: [''],
  });

  ngOnInit(): void {
    if (!this.auth.canEdit()) this.form.disable();
    this.load();
  }

  load(): void {
    this.ingredientsService.list().subscribe({
      next: (ingredients) => this.ingredients.set(ingredients),
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
    this.productsService.search('', null, null).subscribe((products) => this.products.set(products));
  }

  select(ingredient: Ingredient): void {
    this.selectedId.set(ingredient.id);
    this.form.reset({ name: ingredient.name, allergen: ingredient.allergen ?? '' });
    this.dialogOpen.set(true);
  }

  startCreate(): void {
    this.selectedId.set(null);
    this.form.reset({ name: '', allergen: '' });
    this.dialogOpen.set(true);
  }

  closeDialog(): void {
    this.dialogOpen.set(false);
    this.selectedId.set(null);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const body = { name: value.name, allergen: value.allergen || null };
    const id = this.selectedId();
    const request = id ? this.ingredientsService.update(id, body) : this.ingredientsService.create(body);
    request.subscribe({
      next: (saved) => {
        this.notify.success(`Ingrediente "${saved.name}" guardado`);
        this.closeDialog();
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  async remove(): Promise<void> {
    const ingredient = this.selected();
    if (!ingredient) return;
    const accepted = await this.confirmService.ask({
      title: 'Eliminar ingrediente',
      message: `¿Seguro que quieres eliminar "${ingredient.name}"? Esta acción no se puede deshacer.`,
    });
    if (!accepted) return;
    this.ingredientsService.remove(ingredient.id).subscribe({
      next: () => {
        this.notify.success('Ingrediente eliminado');
        this.closeDialog();
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }
}
