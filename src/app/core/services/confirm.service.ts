import { Injectable, signal } from '@angular/core';

export interface ConfirmOptions {
  title: string;
  message: string;
  /** Texto del boton de aceptar (por defecto "Eliminar"). */
  confirmText?: string;
  /** true (por defecto) = accion destructiva, en rojo; false = accion normal. */
  danger?: boolean;
}

interface PendingConfirm extends Required<ConfirmOptions> {
  resolve: (accepted: boolean) => void;
}

/**
 * Dialogo de confirmacion propio de la app (sustituye al confirm() del navegador).
 *
 *   if (!(await this.confirmService.ask({ title: 'Eliminar producto', message: '...' }))) return;
 */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly pending = signal<PendingConfirm | null>(null);

  ask(options: ConfirmOptions): Promise<boolean> {
    this.pending()?.resolve(false);
    return new Promise((resolve) =>
      this.pending.set({ confirmText: 'Eliminar', danger: true, ...options, resolve }),
    );
  }

  answer(accepted: boolean): void {
    this.pending()?.resolve(accepted);
    this.pending.set(null);
  }
}
