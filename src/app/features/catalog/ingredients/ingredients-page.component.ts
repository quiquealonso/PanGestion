import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Ingredient } from '../../../core/models/ingredient.model';
import { AuthService } from '../../../core/services/auth.service';
import { IngredientsService } from '../../../core/services/ingredients.service';
import { NotificationService } from '../../../core/services/notification.service';
import { apiErrorMessage } from '../../../core/utils/api-error';

@Component({
  imports: [CurrencyPipe, DecimalPipe, FormsModule, ReactiveFormsModule],
  selector: 'app-ingredients-page',
  templateUrl: './ingredients-page.component.html',
  styles: '.stock-adjust { margin-top: 1.4rem; } .stock-adjust .toolbar { margin: 0; }',
})
export class IngredientsPageComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly ingredientsService = inject(IngredientsService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);

  readonly ingredients = signal<Ingredient[]>([]);
  readonly selectedId = signal<number | null>(null);
  readonly onlyLowStock = signal(false);
  stockAmount = 0;

  readonly selected = computed(() => this.ingredients().find((i) => i.id === this.selectedId()) ?? null);
  readonly visible = computed(() => this.ingredients().filter((i) => !this.onlyLowStock() || i.lowStock));
  readonly lowStockCount = computed(() => this.ingredients().filter((i) => i.lowStock).length);
  readonly inventoryValue = computed(() => this.ingredients().reduce((sum, i) => sum + i.stock * i.unitCost, 0));

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    unit: ['kg', [Validators.required, Validators.maxLength(10)]],
    stock: [0, [Validators.required, Validators.min(0)]],
    minStock: [0, [Validators.required, Validators.min(0)]],
    unitCost: [0, [Validators.required, Validators.min(0)]],
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
  }

  select(ingredient: Ingredient): void {
    this.selectedId.set(ingredient.id);
    this.stockAmount = 0;
    this.form.reset({ ...ingredient, allergen: ingredient.allergen ?? '' });
  }

  startCreate(): void {
    this.selectedId.set(null);
    this.form.reset({ name: '', unit: 'kg', stock: 0, minStock: 0, unitCost: 0, allergen: '' });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const id = this.selectedId();
    const value = this.form.getRawValue();
    const body = { ...value, allergen: value.allergen || null };
    const request = id ? this.ingredientsService.update(id, body) : this.ingredientsService.create(body);
    request.subscribe({
      next: (saved) => {
        this.notify.success(`Ingrediente "${saved.name}" guardado`);
        this.selectedId.set(saved.id);
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  /** Entrada (+) o salida (-) de stock sin editar la ficha completa. */
  adjustStock(sign: 1 | -1): void {
    const ingredient = this.selected();
    if (!ingredient || !this.stockAmount) return;
    this.ingredientsService.adjustStock(ingredient.id, sign * Math.abs(this.stockAmount)).subscribe({
      next: (updated) => {
        this.ingredients.update((list) => list.map((i) => (i.id === updated.id ? updated : i)));
        this.select(updated);
        this.notify.success(`Stock de ${updated.name}: ${updated.stock} ${updated.unit}`);
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  remove(): void {
    const ingredient = this.selected();
    if (!ingredient || !confirm(`¿Eliminar el ingrediente "${ingredient.name}"?`)) return;
    this.ingredientsService.remove(ingredient.id).subscribe({
      next: () => {
        this.notify.success('Ingrediente eliminado');
        this.startCreate();
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }
}
