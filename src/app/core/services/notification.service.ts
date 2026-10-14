import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: number;
  type: 'success' | 'error';
  text: string;
}

/** Mensajes breves (toasts) que se muestran en la esquina de la pantalla. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private nextId = 1;
  readonly toasts = signal<Toast[]>([]);

  success(text: string): void {
    this.push('success', text);
  }

  error(text: string): void {
    this.push('error', text);
  }

  dismiss(id: number): void {
    this.toasts.update((toasts) => toasts.filter((toast) => toast.id !== id));
  }

  private push(type: Toast['type'], text: string): void {
    const id = this.nextId++;
    this.toasts.update((toasts) => [...toasts, { id, type, text }]);
    setTimeout(() => this.dismiss(id), 4500);
  }
}
