import { NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, HostListener, computed, effect, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { ROLE_LABELS } from '../../core/models/user.model';
import { AuthService } from '../../core/services/auth.service';
import { LiveEventsService } from '../../core/services/live-events.service';
import { NotificationService } from '../../core/services/notification.service';
import { ThemeService } from '../../core/services/theme.service';
import { ReadyAlertsComponent } from '../../shared/ready-alerts.component';

/** Se recuerda en el navegador si el menu lateral esta plegado (pantallas fijas como la de cocina). */
const COLLAPSED_KEY = 'pangestion.sidebar-collapsed';

/** Boton del menu inferior del movil. */
interface Tab {
  label: string;
  icon: string;
  route: string;
  exact?: boolean;
}

@Component({
  imports: [NgTemplateOutlet, RouterLink, RouterLinkActive, RouterOutlet, ReadyAlertsComponent],
  selector: 'app-shell',
  styleUrl: './shell.component.scss',
  templateUrl: './shell.component.html',
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  protected readonly notifications = inject(NotificationService);
  protected readonly theme = inject(ThemeService);
  protected readonly roleLabels = ROLE_LABELS;
  private readonly live = inject(LiveEventsService);

  readonly collapsed = signal(readCollapsed());
  readonly menuOpen = signal(false);
  /** Movil: hoja con el resto del menu y la cuenta */
  readonly sheetOpen = signal(false);

  /**
   * Movil: lo que va en el menu inferior. Camarero, cocina y repartidor tienen todo ahi (y "Yo" para la cuenta);
   * el resto, lo mas usado y "Menu" con todo lo demas.
   */
  readonly tabs = computed<Tab[]>(() => {
    const auth = this.auth;
    if (auth.isSuperAdmin()) {
      return [
        { label: 'Empresas', icon: '🏢', route: '/companies' },
        { label: 'Usuarios', icon: '👥', route: '/users' },
      ];
    }
    if (auth.isWaiter()) {
      return [
        { label: 'Mesas', icon: '🍽️', route: '/service' },
        { label: 'Cobrar', icon: '💶', route: '/bar' },
      ];
    }
    if (auth.isKitchen()) return [{ label: 'Cocina', icon: '🍳', route: '/kitchen' }];
    if (auth.isDriver()) return [{ label: 'Reparto', icon: '🏍️', route: '/delivery' }];
    if (auth.hasDining()) {
      return [
        { label: 'Inicio', icon: '🏠', route: '/dashboard' },
        ...(auth.canServe() ? [{ label: 'Mesas', icon: '🍽️', route: '/service' }] : []),
        { label: 'Pedidos', icon: '🧾', route: '/orders', exact: true },
      ];
    }
    return [
      { label: 'Inicio', icon: '🏠', route: '/dashboard' },
      { label: 'Pedidos', icon: '🧾', route: '/orders', exact: true },
      { label: 'Producción', icon: '🥖', route: '/production' },
    ];
  });
  /** Hay mas pantallas que las del menu inferior (el ultimo boton es "Menu" y no solo la cuenta) */
  readonly hasMore = computed(() => !(this.auth.isWaiter() || this.auth.isKitchen() || this.auth.isDriver()));
  /** Iniciales del usuario (dos como mucho) para el boton de su menu. */
  readonly initials = computed(() =>
    (this.auth.user()?.name ?? '')
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((word) => word.charAt(0).toUpperCase())
      .join('') || '?',
  );

  constructor() {
    // Bares y restaurantes: conexion de avisos en tiempo real mientras haya sesion
    effect(() => {
      // Repartidores de cualquier negocio: avisos de pedidos listos para llevar
      if (this.auth.hasDining() || this.auth.isDriver()) this.live.start();
      else this.live.stop();
    });
    inject(DestroyRef).onDestroy(() => this.live.stop());
    // Al cambiar de pantalla se cierra la hoja del menu del movil
    inject(Router)
      .events.pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => this.sheetOpen.set(false));
  }

  toggleCollapsed(): void {
    this.collapsed.update((value) => !value);
    try {
      localStorage.setItem(COLLAPSED_KEY, String(this.collapsed()));
    } catch {
      // sin almacenamiento: vale para esta visita
    }
  }

  toggleMenu(event: Event): void {
    event.stopPropagation();
    this.menuOpen.update((open) => !open);
  }

  /** Un clic fuera del menu o Escape lo cierran. */
  @HostListener('document:click')
  @HostListener('document:keydown.escape')
  closeMenu(): void {
    this.menuOpen.set(false);
  }
}

function readCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSED_KEY) === 'true';
  } catch {
    return false;
  }
}
