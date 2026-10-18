import { Component, inject, input, output } from '@angular/core';
import { ConfirmService } from '../../core/services/confirm.service';

/**
 * Ventana modal reutilizable. El contenido se proyecta dentro.
 * Se cierra con la X, con la tecla Escape o pulsando fuera.
 *
 *   @if (dialogOpen()) {
 *     <app-modal title="Nueva categoría" (closed)="closeDialog()"> ...formulario... </app-modal>
 *   }
 */
@Component({
  selector: 'app-modal',
  host: { '(document:keydown.escape)': 'onEscape()' },
  template: `
    <div class="backdrop" (click)="closed.emit()">
      <section class="modal" role="dialog" aria-modal="true" [attr.aria-label]="title()"
               [style.max-width]="width()" (click)="$event.stopPropagation()">
        <header class="modal__head">
          <h3>{{ title() }}</h3>
          <button type="button" class="btn btn--small" aria-label="Cerrar" (click)="closed.emit()">✕</button>
        </header>
        <ng-content />
      </section>
    </div>
  `,
  styles: `
    .backdrop {
      position: fixed;
      inset: 0;
      z-index: 40;
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
      padding: 1.4rem;
      border-radius: 1.5rem;
      border: 1px solid var(--border);
      background: var(--panel-strong);
      box-shadow: var(--shadow);
    }

    .modal__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      margin-bottom: 1rem;
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

  /** Escape cierra el modal, salvo que encima haya una confirmacion abierta (esa se cierra antes). */
  protected onEscape(): void {
    if (!this.confirmService.pending()) {
      this.closed.emit();
    }
  }
}
