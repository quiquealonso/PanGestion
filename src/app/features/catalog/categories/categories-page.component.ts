import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Category } from '../../../core/models/category.model';
import { Product, STATION_LABELS, Station } from '../../../core/models/product.model';
import { AuthService } from '../../../core/services/auth.service';
import { CategoriesService } from '../../../core/services/categories.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ProductsService } from '../../../core/services/products.service';
import { apiErrorMessage } from '../../../core/utils/api-error';
import { ModalComponent } from '../../../shared/modal/modal.component';

@Component({
  imports: [CurrencyPipe, ReactiveFormsModule, ModalComponent],
  styles: '.product-row { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: 0.55rem 0; border-bottom: 1px solid var(--border); } .product-row:last-child { border-bottom: 0; }',
  selector: 'app-categories-page',
  templateUrl: './categories-page.component.html',
})
export class CategoriesPageComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly categoriesService = inject(CategoriesService);
  private readonly productsService = inject(ProductsService);
  private readonly notify = inject(NotificationService);
  private readonly confirmService = inject(ConfirmService);
  protected readonly auth = inject(AuthService);

  readonly categories = signal<Category[]>([]);
  /** null = creando una categoria nueva. */
  readonly selectedId = signal<number | null>(null);
  readonly dialogOpen = signal(false);
  /** 'view' = lista de productos de la categoria; 'edit' = formulario. */
  readonly mode = signal<'view' | 'edit'>('view');
  readonly categoryProducts = signal<Product[] | null>(null);
  readonly selected = computed(() => this.categories().find((c) => c.id === this.selectedId()) ?? null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    color: ['#F59E0B', [Validators.required, Validators.pattern(/^#[0-9A-Fa-f]{6}$/)]],
    description: [''],
    /** Bares y restaurantes: donde se preparan sus productos ('' = lo decide cada producto) */
    station: ['' as Station | ''],
  });
  readonly stationLabels = STATION_LABELS;

  ngOnInit(): void {
    if (!this.auth.canEdit()) this.form.disable();
    this.load();
  }

  load(): void {
    this.categoriesService.list().subscribe({
      next: (categories) => this.categories.set(categories),
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  /** Al pulsar una categoria se muestran sus productos. */
  select(category: Category): void {
    this.selectedId.set(category.id);
    this.mode.set('view');
    this.categoryProducts.set(null);
    this.productsService.search('', category.id, null).subscribe({
      next: (products) => this.categoryProducts.set(products),
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
    this.dialogOpen.set(true);
  }

  /** Pasa del listado de productos al formulario de edicion. */
  edit(): void {
    const category = this.selected();
    if (!category) return;
    this.form.reset({ name: category.name, color: category.color, description: category.description ?? '', station: category.station ?? '' });
    this.mode.set('edit');
  }


  startCreate(): void {
    this.selectedId.set(null);
    this.form.reset({ name: '', color: '#F59E0B', description: '', station: '' });
    this.mode.set('edit');
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
    const id = this.selectedId();
    const value = this.form.getRawValue();
    const body = { ...value, station: value.station || null };
    const request = id ? this.categoriesService.update(id, body) : this.categoriesService.create(body);
    request.subscribe({
      next: (saved) => {
        this.notify.success(`Categoría "${saved.name}" guardada`);
        this.closeDialog();
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  /** Sube (-1) o baja (+1) una categoria y guarda el nuevo orden. */
  move(index: number, delta: number): void {
    const list = [...this.categories()];
    const target = index + delta;
    if (target < 0 || target >= list.length) return;
    [list[index], list[target]] = [list[target], list[index]];
    this.categories.set(list);
    this.categoriesService.reorder(list.map((c) => c.id)).subscribe({
      next: (categories) => this.categories.set(categories),
      error: (err) => {
        this.notify.error(apiErrorMessage(err));
        this.load();
      },
    });
  }

  async remove(): Promise<void> {
    const category = this.selected();
    if (!category) return;
    const accepted = await this.confirmService.ask({
      title: 'Eliminar categoría',
      message: `¿Seguro que quieres eliminar "${category.name}"? Esta acción no se puede deshacer.`,
    });
    if (!accepted) return;
    this.categoriesService.remove(category.id).subscribe({
      next: () => {
        this.notify.success('Categoría eliminada');
        this.closeDialog();
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }
}
