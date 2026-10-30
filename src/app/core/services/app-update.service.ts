import { Injectable, inject, signal } from '@angular/core';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { APP_VERSION } from '../../../environments/version';

/** Cada cuanto se pregunta al servidor si hay version nueva (ademas de al abrir la app y al volver a ella). */
const CHECK_EVERY_MS = 5 * 60 * 1000;

/**
 * Actualizaciones de la app instalada (PWA). El service worker compara la version que tiene con la publicada
 * (ngsw.json). Cuando hay un despliegue nuevo la descarga en segundo plano y aqui se avisa con el boton
 * "Actualizar", que activa la version nueva y recarga la pagina.
 */
@Injectable({ providedIn: 'root' })
export class AppUpdateService {
  private readonly updates = inject(SwUpdate);

  /** Version que se esta usando (la de este build) */
  readonly current = APP_VERSION;
  /** Version nueva ya descargada y lista para usar; null = estas en la ultima */
  readonly available = signal<string | null>(null);
  readonly updating = signal(false);

  start(): void {
    if (!this.updates.isEnabled) return;

    this.updates.versionUpdates.subscribe((event) => {
      if (event.type === 'VERSION_READY') {
        const data = (event as VersionReadyEvent).latestVersion.appData as { version?: string } | undefined;
        this.available.set(data?.version ?? 'nueva');
      }
    });
    // La cache de la app quedo rota (por ejemplo, el navegador borro ficheros): solo se arregla recargando
    this.updates.unrecoverable.subscribe(() => document.location.reload());

    const check = () => this.updates.checkForUpdate().catch(() => undefined);
    setInterval(check, CHECK_EVERY_MS);
    // En el movil la app pasa horas en segundo plano: al volver a ella se comprueba enseguida
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') check();
    });
  }

  /** Activa la version nueva y recarga (equivale a un "hard refresh" de la app). */
  async update(): Promise<void> {
    this.updating.set(true);
    try {
      await this.updates.activateUpdate();
    } finally {
      document.location.reload();
    }
  }
}
