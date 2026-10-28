import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Category } from '../../../core/models/category.model';
import { STATION_LABELS } from '../../../core/models/product.model';
import { AuthService } from '../../../core/services/auth.service';
import { CategoriesService } from '../../../core/services/categories.service';
import { NotificationService } from '../../../core/services/notification.service';
import { apiErrorMessage } from '../../../core/utils/api-error';

/**
 * Orden de las categorias en la pantalla de cocina (bares y restaurantes): dentro de cada ticket, los productos
 * salen por este orden (los entrantes antes que los principales). No cambia el orden del catalogo ni de la web.
 */
@Component({
  selector: 'app-kitchen-order-page',
  template: `
    <section class="page">
      <header class="page-head">
        <div>
          <p class="eyebrow">Catálogo</p>
          <h2>Orden en cocina</h2>
          <p>En qué orden salen las categorías dentro de cada ticket de la cocina: primero lo que va antes a la mesa.
            No cambia el orden de la carta ni de la web de pedidos.</p>
        </div>
      </header>

      <article class="panel">
        <ol class="order-list">
          @for (category of ordered(); track category.id; let i = $index, first = $first, last = $last) {
            <li class="order-row" [style.--c]="category.color">
              <span class="order-row__pos">{{ i + 1 }}</span>
              <span class="badge" [style.background]="category.color">{{ category.name.charAt(0) }}</span>
              <div class="order-row__name">
                <strong>{{ category.name }}</strong>
                <small>{{ category.productCount }} productos
                  @if (category.station && category.station !== 'COCINA') { · {{ stationLabels[category.station].toLowerCase() }} (no sale en cocina) }
                </small>
              </div>
              @if (auth.canEdit()) {
                <span class="sort-buttons">
                  <button type="button" class="btn btn--small" [disabled]="first || saving()" (click)="move(i, -1)"
                          [attr.aria-label]="'Subir ' + category.name">▲</button>
                  <button type="button" class="btn btn--small" [disabled]="last || saving()" (click)="move(i, 1)"
                          [attr.aria-label]="'Bajar ' + category.name">▼</button>
                </span>
              }
            </li>
          } @empty {
            <p class="empty">No hay categorías.</p>
          }
        </ol>
      </article>
    </section>
  `,
  styles: `
    .order-list {
      display: grid;
      gap: 0.5rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .order-row {
      display: flex;
      align-items: center;
      gap: 0.8rem;
      padding: 0.6rem 0.8rem;
      border: 1px solid var(--border);
      border-left: 4px solid var(--c);
      border-radius: 0.8rem;
      background: rgba(var(--tint-rgb), 0.03);
    }

    .order-row__pos {
      width: 1.6rem;
      color: var(--muted);
      font-family: 'Space Grotesk', sans-serif;
      font-weight: 700;
      text-align: center;
    }

    .order-row__name {
      display: grid;
      flex: 1;
      min-width: 0;

      small {
        color: var(--muted);
      }
    }

    .sort-buttons {
      display: flex;
      gap: 0.3rem;
    }
  `,
})
export class KitchenOrderPageComponent implements OnInit {
  private readonly categoriesService = inject(CategoriesService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);
  readonly stationLabels = STATION_LABELS;

  readonly categories = signal<Category[]>([]);
  readonly saving = signal(false);
  /** Por orden de cocina; a igualdad, el del catalogo. */
  readonly ordered = computed(() =>
    [...this.categories()].sort((a, b) => a.kitchenOrder - b.kitchenOrder || a.sortOrder - b.sortOrder),
  );

  ngOnInit(): void {
    this.categoriesService.list().subscribe({
      next: (categories) => this.categories.set(categories),
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  /** Sube o baja una categoria y guarda el orden entero. Se ve al momento; si falla, se vuelve atras. */
  move(index: number, delta: number): void {
    const list = [...this.ordered()];
    const target = index + delta;
    if (target < 0 || target >= list.length) return;
    [list[index], list[target]] = [list[target], list[index]];
    const previous = this.categories();
    this.categories.set(list.map((c, i) => ({ ...c, kitchenOrder: i })));
    this.saving.set(true);
    this.categoriesService.reorderKitchen(list.map((c) => c.id)).subscribe({
      next: (categories) => {
        this.categories.set(categories);
        this.saving.set(false);
      },
      error: (err) => {
        this.categories.set(previous);
        this.saving.set(false);
        this.notify.error(apiErrorMessage(err));
      },
    });
  }
}
