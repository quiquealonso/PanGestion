import { Component, inject } from '@angular/core';
import { ConfirmService } from '../core/services/confirm.service';

/** Pinta el dialogo de ConfirmService. Se coloca una sola vez, en app.html. */
@Component({
  selector: 'app-confirm-dialog',
  host: { '(document:keydown.escape)': 'onEscape($event)' },
  template: `
    @if (confirmService.pending(); as dialog) {
      <div class="backdrop" (click)="confirmService.answer(false)">
        <section class="dialog" [class.dialog--normal]="!dialog.danger" role="alertdialog" aria-modal="true" [attr.aria-label]="dialog.title"
                 (click)="$event.stopPropagation()">
          <div class="icon">{{ dialog.danger ? '!' : '?' }}</div>
          <h3>{{ dialog.title }}</h3>
          <p>{{ dialog.message }}</p>
          <footer>
            <button type="button" class="btn" (click)="confirmService.answer(false)">Cancelar</button>
            <button type="button" class="btn" [class.btn--danger]="dialog.danger" [class.btn--solid]="dialog.danger"
                    [class.btn--primary]="!dialog.danger" (click)="confirmService.answer(true)" autofocus>
              {{ dialog.confirmText }}
            </button>
          </footer>
        </section>
      </div>
    }
  `,
  styles: `
    .backdrop {
      position: fixed;
      inset: 0;
      z-index: 60;
      display: grid;
      place-items: center;
      padding: 1rem;
      background: rgba(3, 7, 15, 0.75);
      backdrop-filter: blur(4px);
    }

    .dialog {
      width: 100%;
      max-width: 420px;
      padding: 1.5rem;
      border-radius: 1.4rem;
      border: 1px solid rgba(251, 113, 133, 0.3);
      background: var(--panel-strong);
      box-shadow: var(--shadow);
      text-align: center;
    }

    .icon {
      display: grid;
      place-items: center;
      width: 3rem;
      height: 3rem;
      margin: 0 auto 0.8rem;
      border-radius: 50%;
      background: rgba(251, 113, 133, 0.16);
      color: var(--danger);
      font-family: 'Space Grotesk', sans-serif;
      font-size: 1.4rem;
      font-weight: 700;
    }

    h3 {
      margin: 0 0 0.5rem;
      font-family: 'Space Grotesk', sans-serif;
    }

    p {
      margin: 0 0 1.3rem;
      color: var(--muted);
      line-height: 1.5;
    }

    footer {
      display: flex;
      justify-content: center;
      gap: 0.6rem;
    }

    .dialog--normal {
      border-color: rgba(125, 211, 252, 0.3);
    }

    .dialog--normal .icon {
      background: rgba(125, 211, 252, 0.16);
      color: var(--primary);
    }

    .btn--solid {
      background: rgba(251, 113, 133, 0.85);
      border-color: transparent;
      color: #1f0a10;
      font-weight: 700;
    }
  `,
})
export class ConfirmDialogComponent {
  protected readonly confirmService = inject(ConfirmService);

  /** Escape cancela la confirmacion sin cerrar tambien el dialogo que haya debajo. */
  protected onEscape(event: Event): void {
    if (this.confirmService.pending()) {
      event.stopImmediatePropagation();
      this.confirmService.answer(false);
    }
  }
}
