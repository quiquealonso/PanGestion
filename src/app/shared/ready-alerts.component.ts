import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/services/auth.service';
import { DeliveryEvent, LiveEventsService, ReadyEvent } from '../core/services/live-events.service';
import { beep, vibrate } from './sound';

/** Un aviso en pantalla: listo para servir (camarero) o listo para llevar (repartidor). */
interface ReadyAlert {
  key: number;
  at: Date;
  mine: boolean;
  title: string;
  items: string[];
  hint: string;
  /** A donde lleva al tocarlo */
  route: (string | number)[];
}

/** Avisos que se quedan en pantalla como maximo. */
const MAX_ALERTS = 5;

/**
 * Avisos al camarero cuando cocina o barra deja algo listo ("Mesa 4 · 2× Burger") y al repartidor cuando
 * un pedido a domicilio queda listo para llevar ("🏍️ #57 · 21:30 · C/ Mayor 12").
 * Suenan y vibran, y se quedan hasta que se tocan (llevan a la comanda) o se cierran.
 * Con la pestana oculta, si el usuario lo ha permitido, tambien sale una notificacion del sistema.
 */
@Component({
  selector: 'app-ready-alerts',
  imports: [DatePipe],
  template: `
    <div class="alerts" aria-live="assertive">
      @for (alert of alerts(); track alert.key) {
        <div class="alert" [class.alert--mine]="alert.mine">
          <button type="button" class="alert__main" (click)="open(alert)">
            <span class="alert__title">{{ alert.title }} <small>{{ alert.at | date: 'HH:mm' }}</small></span>
            <span class="alert__items">{{ alert.items.join(' · ') }}</span>
            <span class="alert__hint">{{ alert.hint }}</span>
          </button>
          <button type="button" class="alert__close" (click)="dismiss(alert.key)" aria-label="Cerrar aviso">✕</button>
        </div>
      }
    </div>
  `,
  styles: `
    .alerts {
      position: fixed;
      top: 1rem;
      right: 1rem;
      z-index: 60;
      display: grid;
      gap: 0.6rem;
      width: min(360px, calc(100vw - 2rem));
    }

    .alert {
      display: flex;
      border-radius: 1rem;
      border: 1px solid rgba(var(--success-rgb), 0.55);
      background: var(--panel-strong);
      box-shadow: var(--shadow);
      animation: drop 220ms ease-out;
    }

    .alert--mine {
      border-width: 2px;
      border-color: var(--success);
    }

    .alert__main {
      display: grid;
      flex: 1;
      gap: 0.15rem;
      padding: 0.75rem 0.9rem;
      border: 0;
      background: none;
      color: var(--text);
      font: inherit;
      text-align: left;
      cursor: pointer;
    }

    .alert__title {
      font-family: 'Space Grotesk', sans-serif;
      font-size: 1.1rem;
      font-weight: 700;

      small {
        color: var(--muted);
        font-size: 0.75rem;
        font-weight: 400;
      }
    }

    .alert__items {
      font-weight: 600;
    }

    .alert__hint {
      color: var(--success-text);
      font-size: 0.8rem;
    }

    .alert__close {
      width: 2.6rem;
      border: 0;
      background: none;
      color: var(--muted);
      cursor: pointer;
    }

    @keyframes drop {
      from {
        transform: translateY(-12px);
        opacity: 0;
      }
    }
  `,
})
export class ReadyAlertsComponent implements OnInit {
  private readonly live = inject(LiveEventsService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly alerts = signal<ReadyAlert[]>([]);
  private nextKey = 1;

  ngOnInit(): void {
    const ready = this.live.ready$.subscribe((event) => {
      if (this.auth.canServe()) this.addReady(event);
    });
    const delivery = this.live.delivery$.subscribe((event) => {
      if (this.auth.isDriver()) this.addDelivery(event);
    });
    this.destroyRef.onDestroy(() => {
      ready.unsubscribe();
      delivery.unsubscribe();
    });
  }

  open(alert: ReadyAlert): void {
    this.dismiss(alert.key);
    this.router.navigate(alert.route);
  }

  dismiss(key: number): void {
    this.alerts.update((alerts) => alerts.filter((a) => a.key !== key));
  }

  private addReady(event: ReadyEvent): void {
    const mine = !!event.waiterName && event.waiterName === this.auth.user()?.name;
    const hint = 'Listo para servir' + (mine ? ' · tu mesa' : event.waiterName ? ' · mesa de ' + event.waiterName : '');
    this.add({ mine, title: `🔔 Mesa ${event.tableLabel}`, items: event.items, hint, route: ['/service', event.sessionId] },
      `Mesa ${event.tableLabel}: listo para servir`, `ready-${event.sessionId}`);
  }

  private addDelivery(event: DeliveryEvent): void {
    this.add({ mine: true, title: `🏍️ #${event.orderId} · ${event.time.slice(0, 5)}`, items: [event.customerName, event.address],
      hint: 'Listo para llevar', route: ['/delivery'] },
      `Pedido #${event.orderId} listo para llevar`, `delivery-${event.orderId}`);
  }

  private add(alert: Omit<ReadyAlert, 'key' | 'at'>, systemTitle: string, tag: string): void {
    this.alerts.update((alerts) => [{ ...alert, key: this.nextKey++, at: new Date() }, ...alerts].slice(0, MAX_ALERTS));
    beep();
    vibrate();
    if (document.hidden && 'Notification' in window && Notification.permission === 'granted') {
      new Notification(systemTitle, { body: alert.items.join(' · '), tag });
    }
  }
}
