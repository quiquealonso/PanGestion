import { CurrencyPipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { Extra } from '../core/models/ingredient.model';

/**
 * Extras que se pueden anadir a un producto ("+ Huevo 1,00 €"): chips que se marcan y desmarcan.
 * Lo usan la comanda, el alta de pedidos y la web publica. Si no hay extras, no pinta nada.
 */
@Component({
  selector: 'app-extras-picker',
  imports: [CurrencyPipe],
  template: `
    @if (extras().length) {
      <div class="extras">
        <span class="extras__label">Extras (toca para añadir)</span>
        <div class="chips">
          @for (extra of extras(); track extra.id) {
            @let on = selected().includes(extra.id);
            <button type="button" class="chip extra" [class.on]="on" [attr.aria-pressed]="on" (click)="toggled.emit(extra.id)">
              {{ on ? '✓' : '+' }} {{ extra.name }}
              <small>{{ extra.price ? '+' + (extra.price | currency: 'EUR') : 'gratis' }}</small>
            </button>
          }
        </div>
      </div>
    }
  `,
  styles: `
    .extras {
      display: grid;
      gap: 0.45rem;
    }

    .extras__label {
      color: var(--muted);
      font-size: 0.85rem;
    }

    .extra small {
      margin-left: 0.3rem;
      color: var(--muted);
    }

    .extra.on {
      border-color: rgba(var(--success-rgb), 0.55);
      background: rgba(var(--success-rgb), 0.16);
      color: var(--success-text);
    }
  `,
})
export class ExtrasPickerComponent {
  readonly extras = input.required<Extra[]>();
  readonly selected = input.required<number[]>();
  readonly toggled = output<number>();
}

/** Precio de unos extras elegidos (por id). */
export function extrasPrice(extras: Extra[], ids: number[]): number {
  return extras.filter((e) => ids.includes(e.id)).reduce((sum, e) => sum + e.price, 0);
}

/** "con huevo, sin cebolla": lo que se anade y lo que se quita, para mostrar una linea. */
export function customizationText(removed: string[], extras: string[]): string {
  return [...extras.map((e) => 'con ' + e.toLowerCase()), ...removed.map((r) => 'sin ' + r.toLowerCase())].join(', ');
}

/** Marca o desmarca un id en una lista ordenada (sin repetir). */
export function toggleId(ids: number[], id: number): number[] {
  return (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]).sort((a, b) => a - b);
}
