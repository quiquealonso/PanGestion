import { Component, DOCUMENT, DestroyRef, inject, input, output } from '@angular/core';
import { ConfirmService } from '../../core/services/confirm.service';

/**
 * Ventana modal reutilizable. El contenido se proyecta dentro.
 * Se cierra con la X, con la tecla Escape o pulsando fuera.
 * La cabecera se queda fija arriba al desplazar el contenido; un elemento con el atributo `modal-actions`
 * va en ella, al lado de la X (el boton principal, para no tener que bajar a buscarlo).
 * Mientras esta abierta, la pagina de detras no se desplaza.
 *
 *   @if (dialogOpen()) {
 *     <app-modal title="Nueva categoría" (closed)="closeDialog()"> ...formulario... </app-modal>
 *   }
 */
/** Modales abiertas ahora mismo (puede haber una encima de otra). */
let openModals = 0;

@Component({
  selector: 'app-modal',
  host: { '(document:keydown.escape)': 'onEscape()' },
  template: `
    <div class="backdrop" (click)="closed.emit()">
      <section class="modal" role="dialog" aria-modal="true" [attr.aria-label]="title()"
               [style.max-width]="width()" (click)="$event.stopPropagation()">
        <header class="modal__head">
          <h3>{{ title() }}</h3>
          <div class="modal__actions">
            <ng-content select="[modal-actions]" />
            <button type="button" class="btn btn--small" aria-label="Cerrar" (click)="closed.emit()">✕</button>
          </div>
        </header>
        <ng-content />
      </section>
    </div>
  `,
  styles: `
    .backdrop {
      position: fixed;
      inset: 0;
      z-index: 58; /* por encima del menu inferior y de la carta a pantalla completa del movil */
      display: grid;
      place-items: center;
      padding: 1rem;
      background: rgba(3, 7, 15, 0.7);
      backdrop-filter: blur(4px);
    }

    .modal {
      width: 100%;
      max-height: calc(100vh - 2rem);
      overflow: auto;
      overscroll-behavior: contain;
      padding: 1.4rem;
      border-radius: 1.5rem;
      border: 1px solid var(--border);
      background: var(--panel-strong);
      box-shadow: var(--shadow);
    }

    .modal__head {
      position: sticky;
      top: -1.4rem;
      z-index: 2;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
      margin: -1.4rem -1.4rem 1rem;
      padding: 1.1rem 1.4rem 0.75rem;
      border-bottom: 1px solid var(--border);
      background: var(--panel-strong);
    }

    .modal__actions {
      display: flex;
      flex-shrink: 0;
      align-items: center;
      gap: 0.4rem;
    }

    .modal__head h3 {
      margin: 0;
      font-family: 'Space Grotesk', sans-serif;
    }
  `,
})
export class ModalComponent {
  readonly title = input.required<string>();
  readonly width = input('560px');
  readonly closed = output<void>();

  private readonly confirmService = inject(ConfirmService);

  constructor() {
    // Bloquea el desplazamiento de la pagina (clase en <html>, ver styles.scss). Con varias abiertas, hasta cerrar la ultima.
    const html = inject(DOCUMENT).documentElement;
    openModals++;
    html.classList.add('is-scroll-locked');
    inject(DestroyRef).onDestroy(() => {
      if (--openModals === 0) html.classList.remove('is-scroll-locked');
    });
  }

  /** Escape cierra el modal, salvo que encima haya una confirmacion abierta (esa se cierra antes). */
  protected onEscape(): void {
    if (!this.confirmService.pending()) {
      this.closed.emit();
    }
  }
}
