import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TableShape } from '../core/models/dining.model';

/** Lo que hace falta para dibujar una mesa. */
export interface TableShapeInfo {
  name: string;
  seats: number;
  shape: TableShape;
  width: number;
  height: number;
}

/**
 * Dibujo de una mesa del plano: tablero con su nombre y las sillas alrededor.
 * Ocupa todo su contenedor; quien la usa la coloca en el plano y define --cell (lado de una casilla),
 * con el que escalan las sillas y la letra.
 */
@Component({
  selector: 'app-dining-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (chair of chairs(); track $index) {
      <i class="chair" [style.left.%]="chair.left" [style.top.%]="chair.top"></i>
    }
    <div class="top">
      <strong>{{ table().name }}</strong>
      @if (detail(); as text) {
        <small>{{ text }}</small>
      }
    </div>
  `,
  styles: `
    :host {
      position: relative;
      display: block;
      height: 100%;
    }

    .top {
      position: relative;
      z-index: 1;
      display: grid;
      place-content: center;
      height: 100%;
      border-radius: 0.6rem;
      border: 1.5px solid rgba(var(--primary-rgb), 0.45);
      background: var(--panel-strong);
      box-shadow: 0 6px 16px rgba(0, 0, 0, 0.18);
      text-align: center;
      line-height: 1.1;
      overflow: hidden;
    }

    strong {
      font-family: 'Space Grotesk', sans-serif;
      font-size: min(1.1rem, calc(var(--cell) * 0.6));
    }

    small {
      color: var(--muted);
      font-size: min(0.72rem, calc(var(--cell) * 0.36));
      white-space: nowrap;
    }

    :host(.round) .top {
      border-radius: 50%;
    }

    .chair {
      position: absolute;
      width: calc(var(--cell) * 0.42);
      height: calc(var(--cell) * 0.42);
      border-radius: 3px;
      background: rgba(var(--muted-rgb), 0.55);
      transform: translate(-50%, -50%);
    }

    :host(.round) .chair {
      border-radius: 50%;
    }

    /* Estados: seleccionada, posicion no valida, ocupada */
    :host(.selected) .top {
      border-color: var(--primary);
      background: rgba(var(--primary-rgb), 0.18);
      box-shadow: 0 0 0 3px rgba(var(--primary-rgb), 0.25), 0 8px 20px rgba(0, 0, 0, 0.22);
    }

    :host(.invalid) .top {
      border-color: var(--danger);
      background: rgba(var(--danger-rgb), 0.18);
    }

    :host(.busy) .top {
      border-color: var(--accent);
      background: rgba(var(--accent-rgb), 0.2);
    }

    :host(.busy) .chair {
      background: rgba(var(--accent-rgb), 0.75);
    }

    /* Ocupada con algo listo para servir: borde verde que late */
    :host(.ready) .top {
      border-color: var(--success);
      box-shadow: 0 0 0 0 rgba(var(--success-rgb), 0.6);
      animation: pulse 1.6s ease-out infinite;
    }

    @keyframes pulse {
      to {
        box-shadow: 0 0 0 10px rgba(var(--success-rgb), 0);
      }
    }
  `,
  host: {
    '[class.round]': "table().shape === 'ROUND'",
    '[class.selected]': 'selected()',
    '[class.invalid]': 'invalid()',
    '[class.busy]': 'busy()',
    '[class.ready]': 'ready()',
  },
})
export class DiningTableComponent {
  readonly table = input.required<TableShapeInfo>();
  /** Segunda linea: por defecto las plazas ("4 pers"); en las mesas de una casilla no cabe. */
  readonly info = input<string | null>(null);
  readonly selected = input(false);
  readonly invalid = input(false);
  readonly busy = input(false);
  /** Tiene productos listos para llevar a la mesa */
  readonly ready = input(false);

  readonly detail = computed(() => {
    const t = this.table();
    if (t.width * t.height <= 1) return null;
    return this.info() ?? `${t.seats} pers`;
  });

  /** Sillas alrededor de la mesa: posicion en % de la mesa (sobre su borde). */
  readonly chairs = computed(() => {
    const table = this.table();
    const n = table.seats;
    if (table.shape === 'ROUND') {
      return Array.from({ length: n }, (_, i) => {
        const angle = (2 * Math.PI * i) / n - Math.PI / 2;
        return { left: 50 + 50 * Math.cos(angle), top: 50 + 50 * Math.sin(angle) };
      });
    }
    // Rectangular: repartidas por el perimetro, empezando por el lado de arriba
    const { width: w, height: h } = table;
    const perimeter = 2 * (w + h);
    return Array.from({ length: n }, (_, i) => {
      let p = ((i + 0.5) * perimeter) / n;
      if (p < w) return { left: (p / w) * 100, top: 0 };
      p -= w;
      if (p < h) return { left: 100, top: (p / h) * 100 };
      p -= h;
      if (p < w) return { left: 100 - (p / w) * 100, top: 100 };
      p -= w;
      return { left: 0, top: 100 - (p / h) * 100 };
    });
  });
}
