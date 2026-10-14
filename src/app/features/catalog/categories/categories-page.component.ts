import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Category } from '../../../core/models/category.model';
import { AuthService } from '../../../core/services/auth.service';
import { CategoriesService } from '../../../core/services/categories.service';
import { NotificationService } from '../../../core/services/notification.service';
import { apiErrorMessage } from '../../../core/utils/api-error';

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-categories-page',
  templateUrl: './categories-page.component.html',
})
export class CategoriesPageComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly categoriesService = inject(CategoriesService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);

  readonly categories = signal<Category[]>([]);
  /** null = creando una categoria nueva. */
  readonly selectedId = signal<number | null>(null);
  readonly selected = computed(() => this.categories().find((c) => c.id === this.selectedId()) ?? null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    color: ['#F59E0B', [Validators.required, Validators.pattern(/^#[0-9A-Fa-f]{6}$/)]],
    description: [''],
  });

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

  select(category: Category): void {
    this.selectedId.set(category.id);
    this.form.reset({ name: category.name, color: category.color, description: category.description ?? '' });
  }

  startCreate(): void {
    this.selectedId.set(null);
    this.form.reset({ name: '', color: '#F59E0B', description: '' });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const id = this.selectedId();
    const body = this.form.getRawValue();
    const request = id ? this.categoriesService.update(id, body) : this.categoriesService.create(body);
    request.subscribe({
      next: (saved) => {
        this.notify.success(`Categoría "${saved.name}" guardada`);
        this.selectedId.set(saved.id);
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  remove(): void {
    const category = this.selected();
    if (!category || !confirm(`¿Eliminar la categoría "${category.name}"?`)) return;
    this.categoriesService.remove(category.id).subscribe({
      next: () => {
        this.notify.success('Categoría eliminada');
        this.startCreate();
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }
}
