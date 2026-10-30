import { Component, inject, signal } from '@angular/core';
import { AppUpdateService } from '../core/services/app-update.service';
import { PwaInstallService } from '../core/services/pwa-install.service';

/**
 * Avisos de la app instalable, arriba de la pantalla (abajo estan las barras de botones) y con el mismo formato:
 * - "Hay una version nueva" con boton Actualizar (siempre tiene prioridad)
 * - "Instala la app" con boton Instalar (solo en el movil, si aun no esta instalada)
 */
@Component({
  selector: 'app-pwa-banner',
  template: `
    @if (update.available(); as version) {
      <div class="pwa-banner" role="status" aria-live="polite">
        <span class="pwa-banner__icon" aria-hidden="true">✨</span>
        <div class="pwa-banner__text">
          <strong>Hay una versión nueva</strong>
          <small>{{ version === 'nueva' ? 'Actualiza para usarla' : 'Versión ' + version }} · tienes la {{ update.current.version }}</small>
        </div>
        <button type="button" class="pwa-banner__action" (click)="update.update()" [disabled]="update.updating()">
          {{ update.updating() ? 'Actualizando…' : 'Actualizar' }}
        </button>
      </div>
    } @else if (install.visible()) {
      <div class="pwa-banner" role="dialog" aria-label="Instalar la app">
        <span class="pwa-banner__icon" aria-hidden="true">📲</span>
        <div class="pwa-banner__text">
          <strong>Instala la app</strong>
          @if (install.canPrompt()) {
            <small>Ábrela desde la pantalla de inicio, a pantalla completa</small>
          } @else if (showIosSteps()) {
            <small>Pulsa <b>Compartir</b> <span aria-hidden="true">⎙</span> y luego <b>Añadir a pantalla de inicio</b></small>
          } @else {
            <small>Tenla en la pantalla de inicio de tu iPhone</small>
          }
        </div>
        @if (install.canPrompt()) {
          <button type="button" class="pwa-banner__action" (click)="install.install()">Instalar</button>
        } @else if (!showIosSteps()) {
          <button type="button" class="pwa-banner__action" (click)="showIosSteps.set(true)">Cómo</button>
        }
        <button type="button" class="pwa-banner__close" (click)="install.dismiss()" aria-label="Ahora no">✕</button>
      </div>
    }
  `,
  styles: `
    .pwa-banner {
      position: fixed;
      left: 50%;
      top: calc(10px + env(safe-area-inset-top));
      transform: translateX(-50%);
      z-index: 1000;
      width: min(560px, calc(100vw - 24px));
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 12px 12px 14px;
      border: 1px solid var(--border);
      border-radius: 16px;
      background: var(--panel-strong);
      color: var(--text);
      box-shadow: var(--shadow);
      backdrop-filter: blur(12px);
      animation: pwa-in 0.25s ease-out;
    }
    .pwa-banner__icon { font-size: 1.6rem; line-height: 1; }
    .pwa-banner__text { flex: 1; display: grid; gap: 2px; min-width: 0; }
    .pwa-banner__text small { color: var(--muted); }
    .pwa-banner__action {
      border: 0;
      border-radius: 10px;
      padding: 10px 16px;
      font: inherit;
      font-weight: 600;
      background: var(--accent);
      color: var(--on-primary);
      cursor: pointer;
      white-space: nowrap;
    }
    .pwa-banner__action:disabled { opacity: 0.7; cursor: progress; }
    .pwa-banner__close {
      border: 0;
      background: transparent;
      color: var(--muted);
      font-size: 1rem;
      padding: 8px;
      cursor: pointer;
    }
    @keyframes pwa-in {
      from { opacity: 0; transform: translate(-50%, -12px); }
    }
  `,
})
export class PwaBannerComponent {
  protected readonly update = inject(AppUpdateService);
  protected readonly install = inject(PwaInstallService);
  protected readonly showIosSteps = signal(false);
}
