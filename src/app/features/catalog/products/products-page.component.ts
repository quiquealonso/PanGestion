import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Category } from '../../../core/models/category.model';
import { Ingredient } from '../../../core/models/ingredient.model';
import { Product, STATION_LABELS, Station } from '../../../core/models/product.model';
import { AuthService } from '../../../core/services/auth.service';
import { CategoriesService } from '../../../core/services/categories.service';
import { IngredientsService } from '../../../core/services/ingredients.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ProductsService } from '../../../core/services/products.service';
import { apiErrorMessage } from '../../../core/utils/api-error';
import { Observable, of, switchMap } from 'rxjs';
import { ModalComponent } from '../../../shared/modal/modal.component';
import { AssetUrlPipe } from '../../../shared/asset-url.pipe';

@Component({
  imports: [CurrencyPipe, FormsModule, ReactiveFormsModule, ModalComponent, AssetUrlPipe],
  selector: 'app-products-page',
  templateUrl: './products-page.component.html',
  styles: `
    .chips { display: flex; flex-wrap: wrap; gap: 0.4rem; }
    .chip { cursor: pointer; border: 1px solid transparent; }
    .chip.is-on { border-color: rgba(var(--primary-rgb), 0.6); background: rgba(var(--primary-rgb), 0.18); color: var(--text); }
    .groups { display: grid; gap: 1rem; }
    .photo { display: flex; align-items: center; gap: 1rem; }
    .photo__preview {
      display: grid; place-items: center; flex-shrink: 0; width: 96px; height: 96px; overflow: hidden; border-radius: 1rem;
      color: rgba(9, 17, 31, 0.75); font-family: 'Space Grotesk', sans-serif; font-size: 1.3rem; font-weight: 700;
    }
    .photo__preview img { width: 100%; height: 100%; object-fit: cover; }
    .photo__actions { display: grid; gap: 0.4rem; justify-items: start; }
    .photo__actions label { cursor: pointer; }
  `,
})
export class ProductsPageComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly productsService = inject(ProductsService);
  private readonly categoriesService = inject(CategoriesService);
  private readonly ingredientsService = inject(IngredientsService);
  private readonly notify = inject(NotificationService);
  private readonly confirmService = inject(ConfirmService);
  protected readonly auth = inject(AuthService);

  readonly products = signal<Product[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly ingredients = signal<Ingredient[]>([]);
  readonly selectedId = signal<number | null>(null);
  readonly dialogOpen = signal(false);

  // Foto: se elige en el dialogo y se sube al pulsar Guardar
  readonly imageFile = signal<File | null>(null);
  readonly imagePreview = signal<string | null>(null);
  /** true = el usuario ha quitado la foto que tenia el producto. */
  readonly removeImageOnSave = signal(false);
  readonly selected = computed(() => this.products().find((p) => p.id === this.selectedId()) ?? null);

  /** Productos agrupados por categoria, respetando el orden que llega de la API. */
  readonly groups = computed(() => {
    const groups: { id: number; name: string; color: string; products: Product[] }[] = [];
    for (const product of this.products()) {
      let group = groups.find((g) => g.id === product.categoryId);
      if (!group) {
        group = { id: product.categoryId, name: product.categoryName, color: product.categoryColor, products: [] };
        groups.push(group);
      }
      group.products.push(product);
    }
    return groups;
  });

  filters = { search: '', categoryId: null as number | null, active: null as boolean | null };

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    abbreviation: ['', Validators.maxLength(20)],
    /** false = usa el color de la categoria. */
    ownColor: [false],
    color: ['#F59E0B'],
    categoryId: [0, [Validators.required, Validators.min(1)]],
    price: [0, [Validators.required, Validators.min(0)]],
    active: [true],
    description: [''],
    /** Ingredientes que lleva (ids). */
    ingredientIds: [[] as number[]],
    /** Donde se prepara (solo bares y restaurantes) */
    station: ['COCINA' as Station],
  });
  readonly stationLabels = STATION_LABELS;
  /** Estacion que impone la categoria elegida (Bebidas → barra); null = la decide el producto */
  categoryStation(): Station | null {
    return this.categories().find((c) => c.id === this.form.controls.categoryId.value)?.station ?? null;
  }
  readonly stations = Object.keys(STATION_LABELS) as Station[];


  constructor() {
    // El selector de color solo se puede tocar si el producto tiene color propio
    this.form.controls.ownColor.valueChanges.subscribe((ownColor) => {
      const color = this.form.controls.color;
      if (ownColor && this.auth.canEdit()) {
        color.enable({ emitEvent: false });
      } else {
        color.disable({ emitEvent: false });
      }
    });
    this.form.controls.color.disable({ emitEvent: false });
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

  select(product: Product): void {
    this.selectedId.set(product.id);
    this.form.patchValue({
      name: product.name,
      abbreviation: product.abbreviation ?? '',
      ownColor: product.color !== null,
      color: product.color ?? product.categoryColor,
      categoryId: product.categoryId,
      price: product.price,
      active: product.active,
      description: product.description ?? '',
      ingredientIds: product.ingredients.map((i) => i.id),
      station: product.station,
    });
    this.resetImage(product.imageUrl);
    if (!this.auth.canEdit()) this.form.disable();
    this.dialogOpen.set(true);
  }

  startCreate(): void {
    this.selectedId.set(null);
    this.form.reset({ name: '', abbreviation: '', ownColor: false, color: '#F59E0B', categoryId: this.categories()[0]?.id ?? 0, price: 0, active: true, description: '', ingredientIds: [], station: 'COCINA' });
    this.resetImage(null);
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
    const { ownColor, ...fields } = value;
    const body = {
      ...fields,
      abbreviation: value.abbreviation || null,
      color: ownColor ? value.color : null,
      description: value.description || null,
    };
    const id = this.selectedId();
    const request = id ? this.productsService.update(id, body) : this.productsService.create(body);
    request.pipe(switchMap((saved) => this.saveImage(saved))).subscribe({
      next: (saved) => {
        this.notify.success(`Producto "${saved.name}" guardado`);
        this.closeDialog();
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  /** Foto elegida en el selector: se comprueba aqui y se muestra la vista previa. */
  onImageSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      this.notify.error('Formato no válido: usa una imagen JPG, PNG o WEBP');
      return;
    }
    if (file.size > 2 * 1024 * 1024) {
      this.notify.error('La imagen debe ocupar como máximo 2 MB');
      return;
    }
    this.imageFile.set(file);
    this.imagePreview.set(URL.createObjectURL(file));
    this.removeImageOnSave.set(false);
  }

  clearImage(): void {
    this.removeImageOnSave.set(!!this.selected()?.imageUrl);
    this.imageFile.set(null);
    this.imagePreview.set(null);
  }

  private resetImage(currentUrl: string | null): void {
    this.imageFile.set(null);
    this.imagePreview.set(currentUrl);
    this.removeImageOnSave.set(false);
  }

  /** Tras guardar los datos, sube la foto nueva o borra la que se ha quitado. */
  private saveImage(saved: Product): Observable<Product> {
    const file = this.imageFile();
    if (file) return this.productsService.uploadImage(saved.id, file);
    if (this.removeImageOnSave()) return this.productsService.removeImage(saved.id);
    return of(saved);
  }

  /** Solo se reordena con el listado completo (sin busqueda ni filtro de estado). */
  canReorder(): boolean {
    return this.auth.canEdit() && !this.filters.search && this.filters.active === null;
  }

  /** Sube (-1) o baja (+1) un producto dentro de su categoria y guarda el nuevo orden. */
  move(product: Product, delta: number): void {
    const siblings = this.siblings(product);
    const index = siblings.findIndex((p) => p.id === product.id);
    const target = index + delta;
    if (target < 0 || target >= siblings.length) return;
    [siblings[index], siblings[target]] = [siblings[target], siblings[index]];
    this.productsService.reorder(siblings.map((p) => p.id)).subscribe({
      next: () => this.load(),
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  private siblings(product: Product): Product[] {
    return this.products().filter((p) => p.categoryId === product.categoryId);
  }

  ingredientNames(product: Product): string {
    return product.ingredients.map((i) => i.name).join(', ');
  }

  hasIngredient(id: number): boolean {
    return this.form.controls.ingredientIds.value.includes(id);
  }

  /** Marca o desmarca un ingrediente del producto. */
  toggleIngredient(id: number): void {
    const control = this.form.controls.ingredientIds;
    if (control.disabled) return;
    const ids = control.value;
    control.setValue(ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]);
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

  async remove(): Promise<void> {
    const product = this.selected();
    if (!product) return;
    const accepted = await this.confirmService.ask({
      title: 'Eliminar producto',
      message: `¿Seguro que quieres eliminar "${product.name}"? Esta acción no se puede deshacer.`,
    });
    if (!accepted) return;
    this.productsService.remove(product.id).subscribe({
      next: () => {
        this.notify.success('Producto eliminado');
        this.closeDialog();
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }
}
