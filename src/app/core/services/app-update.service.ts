import { Injectable, inject } from '@angular/core';
import { NavigationStart, Router } from '@angular/router';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { filter, first, switchMap } from 'rxjs';

/**
 * App instalada (PWA): el service worker descarga en segundo plano cada version nueva que se publica.
 * Para no cortar a nadie a mitad de un pedido, se recarga al cambiar de pantalla, no en el momento.
 */
@Injectable({ providedIn: 'root' })
export class AppUpdateService {
  private readonly updates = inject(SwUpdate);
  private readonly router = inject(Router);

  start(): void {
    if (!this.updates.isEnabled) return;
    this.updates.versionUpdates
      .pipe(
        filter((event): event is VersionReadyEvent => event.type === 'VERSION_READY'),
        first(),
        switchMap(() => this.router.events.pipe(filter((event) => event instanceof NavigationStart), first())),
      )
      .subscribe((event) => (document.location.href = new URL(event.url.slice(1), document.baseURI).href));
    // Comprobar si hay version nueva cada 15 min (el camarero puede tener la app abierta todo el turno)
    setInterval(() => this.updates.checkForUpdate().catch(() => undefined), 15 * 60 * 1000);
  }
}
