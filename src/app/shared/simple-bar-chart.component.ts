import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

/** Una barra: etiqueta del eje, valor y el texto del tooltip. */
export interface BarDatum {
  label: string;
  value: number;
  tooltip: string;
}

/**
 * Barras verticales de una sola serie (sin leyenda: el titulo de la tarjeta dice que es).
 * Marcas finas en el color primario con la punta redondeada, rejilla discreta, tooltip al pasar
 * el raton o con el teclado, y una vista de tabla para quien no lee el grafico.
 */
@Component({
  selector: 'app-simple-bar-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="head">
      <button type="button" class="link" (click)="table.set(!table())">{{ table() ? 'Ver gráfico' : 'Ver como tabla' }}</button>
    </div>
    @if (table()) {
      <table class="data">
        <thead><tr><th>{{ labelHeader() }}</th><th>{{ valueHeader() }}</th></tr></thead>
        <tbody>
          @for (d of data(); track d.label) {
            <tr><td>{{ d.label }}</td><td>{{ d.tooltip }}</td></tr>
          }
        </tbody>
      </table>
    } @else {
      <div class="chart" role="img" [attr.aria-label]="ariaLabel()">
        <div class="grid" aria-hidden="true">
          @for (t of ticks(); track $index) {
            <div class="grid__line" [style.bottom.%]="t.pos"><span>{{ t.text }}</span></div>
          }
        </div>
        <div class="bars">
          @for (d of data(); track d.label) {
            <button type="button" class="col" [attr.aria-label]="d.label + ': ' + d.tooltip">
              <span class="bar" [style.height.%]="max() ? (d.value / max()) * 100 : 0"></span>
              <span class="tip">{{ d.label }}<br /><strong>{{ d.tooltip }}</strong></span>
            </button>
          }
        </div>
        <div class="axis" aria-hidden="true">
          @for (d of data(); track d.label; let i = $index) {
            <span>{{ showLabel(i) ? d.label : '' }}</span>
          }
        </div>
      </div>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .head {
      display: flex;
      justify-content: flex-end;
      margin-bottom: 0.4rem;
    }

    .link {
      border: 0;
      background: none;
      color: var(--primary);
      font: inherit;
      font-size: 0.8rem;
      cursor: pointer;
    }

    .chart {
      position: relative;
      padding-left: 2.6rem;
    }

    .grid {
      position: absolute;
      inset: 0 0 1.4rem 2.6rem;
    }

    .grid__line {
      position: absolute;
      right: 0;
      left: 0;
      border-top: 1px solid rgba(var(--line-rgb), 0.14);

      span {
        position: absolute;
        right: calc(100% + 0.4rem);
        transform: translateY(-50%);
        color: var(--muted);
        font-size: 0.7rem;
        white-space: nowrap;
      }
    }

    .bars {
      position: relative;
      display: flex;
      align-items: flex-end;
      gap: 2px;
      height: 180px;
      border-bottom: 1px solid rgba(var(--line-rgb), 0.35);
    }

    .col {
      position: relative;
      display: flex;
      flex: 1;
      align-items: flex-end;
      justify-content: center;
      height: 100%;
      min-width: 0;
      padding: 0;
      border: 0;
      background: none;
      cursor: default;
    }

    .bar {
      width: min(28px, 70%);
      min-height: 0;
      border-radius: 4px 4px 0 0;
      background: var(--primary);
    }

    .col:hover .bar,
    .col:focus-visible .bar {
      filter: brightness(1.15);
    }

    .tip {
      position: absolute;
      bottom: calc(100% + 4px);
      left: 50%;
      z-index: 2;
      display: none;
      padding: 0.35rem 0.55rem;
      border-radius: 0.5rem;
      border: 1px solid var(--border);
      background: var(--panel-strong);
      color: var(--text);
      font-size: 0.78rem;
      white-space: nowrap;
      transform: translateX(-50%);
      box-shadow: var(--shadow);
      pointer-events: none;
    }

    .col:hover .tip,
    .col:focus-visible .tip {
      display: block;
    }

    .axis {
      display: flex;
      gap: 2px;
      height: 1.4rem;
      padding-top: 0.25rem;

      span {
        flex: 1;
        min-width: 0;
        overflow: visible;
        color: var(--muted);
        font-size: 0.7rem;
        text-align: center;
        white-space: nowrap;
      }
    }

    .data {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.85rem;

      th,
      td {
        padding: 0.3rem 0.4rem;
        border-bottom: 1px solid rgba(var(--line-rgb), 0.14);
        text-align: left;
      }

      th:last-child,
      td:last-child {
        text-align: right;
      }
    }
  `,
})
export class SimpleBarChartComponent {
  readonly data = input.required<BarDatum[]>();
  /** Texto de los valores del eje (por ejemplo, euros sin decimales). */
  readonly format = input<(value: number) => string>((v) => String(Math.round(v)));
  readonly ariaLabel = input('Gráfico de barras');
  readonly labelHeader = input('');
  readonly valueHeader = input('Valor');

  readonly table = signal(false);
  readonly max = computed(() => niceMax(Math.max(0, ...this.data().map((d) => d.value))));
  /** 4 lineas de rejilla: 0, 1/3, 2/3 y el maximo redondeado */
  readonly ticks = computed(() => {
    const max = this.max();
    if (!max) return [{ pos: 0, text: this.format()(0) }];
    return [0, 1, 2, 3].map((i) => ({ pos: (i / 3) * 100, text: this.format()((max * i) / 3) }));
  });

  /** Con muchas barras no caben todas las etiquetas: se muestra una de cada n. */
  showLabel(index: number): boolean {
    const step = Math.ceil(this.data().length / 12);
    return index % step === 0;
  }
}

/** Maximo "redondo" del eje (1, 2, 3 o 5 por potencia de 10), divisible en 3 tramos legibles. */
function niceMax(value: number): number {
  if (value <= 0) return 0;
  const power = 10 ** Math.floor(Math.log10(value));
  for (const step of [1.5, 3, 6, 9, 15]) {
    if (step * power >= value) return step * power;
  }
  return 15 * power;
}
