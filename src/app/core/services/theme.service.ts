import { Injectable, effect, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'pangestion.theme';

/**
 * Tema del panel de gestion (claro u oscuro). Por defecto sigue el del sistema; si el usuario lo cambia,
 * se recuerda en este navegador. Se aplica con data-theme en <html> (ver styles.scss).
 * index.html lo aplica tambien antes de arrancar Angular, para que no se vea un parpadeo.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(initialTheme());

  constructor() {
    effect(() => {
      document.documentElement.dataset['theme'] = this.theme();
    });
  }

  toggle(): void {
    const next: Theme = this.theme() === 'dark' ? 'light' : 'dark';
    this.theme.set(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // Sin almacenamiento (modo privado): el cambio dura hasta recargar
    }
  }
}

function initialTheme(): Theme {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'light' || saved === 'dark') return saved;
  } catch {
    // sin almacenamiento: se usa el del sistema
  }
  return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
}
