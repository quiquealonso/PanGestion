import { Injectable, computed, signal } from '@angular/core';

/** Evento de Chrome/Android para instalar la app con un clic (no esta en los tipos de TypeScript). */
interface InstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

declare global {
  interface Window {
    __pgInstallPrompt?: InstallPromptEvent;
  }
}

const DISMISSED_KEY = 'pangestion.install-dismissed';
/** Si el usuario cierra el aviso, no se vuelve a mostrar en estos dias. */
const DISMISS_DAYS = 7;

/**
 * Aviso de "Instalar la app" en el movil.
 * - Android (Chrome, Edge, Samsung...): el navegador permite instalar con un clic (beforeinstallprompt, capturado en pwa-init.js).
 * - iPhone/iPad: Apple no deja instalar desde la web con un boton; se explica como hacerlo (Compartir > Anadir a pantalla de inicio).
 * No sale si ya se abre como app instalada, en ordenador, o si se cerro hace poco.
 */
@Injectable({ providedIn: 'root' })
export class PwaInstallService {
  private readonly promptEvent = signal<InstallPromptEvent | null>(window.__pgInstallPrompt ?? null);
  private readonly dismissed = signal(this.recentlyDismissed());
  private readonly installed = signal(isStandalone());

  readonly isIos = isIos();
  /** Hay que mostrar el aviso: con boton de instalar (Android) o con instrucciones (iOS) */
  readonly visible = computed(
    () => isMobile() && !this.installed() && !this.dismissed() && (this.promptEvent() !== null || this.isIos),
  );
  /** En Android se instala con el boton; en iOS solo se pueden dar instrucciones */
  readonly canPrompt = computed(() => this.promptEvent() !== null);

  constructor() {
    window.addEventListener('pg-installable', () => this.promptEvent.set(window.__pgInstallPrompt ?? null));
    window.addEventListener('appinstalled', () => {
      this.installed.set(true);
      this.promptEvent.set(null);
    });
  }

  async install(): Promise<void> {
    const event = this.promptEvent();
    if (!event) return;
    await event.prompt();
    const choice = await event.userChoice;
    // El aviso de Chrome solo se puede usar una vez
    this.promptEvent.set(null);
    window.__pgInstallPrompt = undefined;
    if (choice.outcome === 'accepted') this.installed.set(true);
  }

  dismiss(): void {
    this.dismissed.set(true);
    try {
      localStorage.setItem(DISMISSED_KEY, String(Date.now()));
    } catch {
      // Sin almacenamiento (modo privado): solo se oculta hasta recargar
    }
  }

  private recentlyDismissed(): boolean {
    try {
      const at = Number(localStorage.getItem(DISMISSED_KEY));
      return !!at && Date.now() - at < DISMISS_DAYS * 24 * 60 * 60 * 1000;
    } catch {
      return false;
    }
  }
}

function isStandalone(): boolean {
  return (
    window.matchMedia?.('(display-mode: standalone)').matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  );
}

function isIos(): boolean {
  // iPadOS se presenta como Mac, pero con pantalla tactil
  return /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
}

function isMobile(): boolean {
  return isIos() || /android|mobile/i.test(navigator.userAgent) || window.matchMedia?.('(pointer: coarse)').matches;
}
