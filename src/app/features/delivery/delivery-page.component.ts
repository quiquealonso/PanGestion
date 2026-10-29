import { CurrencyPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { Delivery } from '../../core/models/delivery.model';
import { AuthService } from '../../core/services/auth.service';
import { DeliveryService } from '../../core/services/delivery.service';
import { LiveEventsService } from '../../core/services/live-events.service';
import { NotificationService } from '../../core/services/notification.service';
import { apiErrorMessage } from '../../core/utils/api-error';

/** Respaldo: la lista se refresca con los avisos en tiempo real; esto solo cubre si se corta la conexion. */
const REFRESH_MS = 60_000;

type Tab = 'ready' | 'mine' | 'kitchen' | 'done';

/**
 * Pantalla del repartidor (pensada para el movil): pedidos a domicilio de hoy.
 * "Para llevar" son los listos sin repartidor; al tocar uno, "Me lo llevo" lo pasa a Transportando a su nombre.
 * En "Mios" estan los que lleva: llamar, abrir el mapa, "Entregado" o "No me lo llevo".
 */
@Component({
  selector: 'app-delivery-page',
  imports: [CurrencyPipe],
  templateUrl: './delivery-page.component.html',
  styleUrl: './delivery-page.component.scss',
})
export class DeliveryPageComponent implements OnInit {
  private readonly delivery = inject(DeliveryService);
  private readonly notify = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly live = inject(LiveEventsService);
  protected readonly auth = inject(AuthService);

  readonly orders = signal<Delivery[]>([]);
  readonly loaded = signal(false);
  readonly tab = signal<Tab>('ready');
  /** Pedido abierto en la hoja de acciones */
  readonly selected = signal<Delivery | null>(null);
  readonly busy = signal(false);

  readonly ready = computed(() => this.orders().filter((o) => o.status === 'LISTO'));
  readonly mine = computed(() => this.orders().filter((o) => o.status === 'TRANSPORTANDO' && o.mine));
  /** En reparto con otro repartidor: se ven en "Mios" solo si son mios */
  readonly kitchen = computed(() => this.orders().filter((o) => o.status === 'PENDIENTE' || o.status === 'EN_PROCESO'));
  readonly done = computed(() => this.orders().filter((o) => o.status === 'ENTREGADO'));
  readonly visible = computed(() => {
    switch (this.tab()) {
      case 'ready':
        return this.ready();
      case 'mine':
        return this.mine();
      case 'kitchen':
        return this.kitchen();
      default:
        return this.done();
    }
  });

  ngOnInit(): void {
    this.load(true);
    const changes = this.live.on('delivery').subscribe(() => this.load());
    const timer = setInterval(() => this.load(), REFRESH_MS);
    this.destroyRef.onDestroy(() => {
      changes.unsubscribe();
      clearInterval(timer);
    });
  }

  /** first = al entrar: si llevo alguno, empiezo viendo los mios. */
  load(first = false): void {
    this.delivery.today().subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.loaded.set(true);
        if (first && this.mine().length) this.tab.set('mine');
        // La hoja abierta muestra el estado nuevo (o se cierra si el pedido ya no esta)
        const open = this.selected();
        if (open) this.selected.set(orders.find((o) => o.id === open.id) ?? null);
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  open(order: Delivery): void {
    this.selected.set(order);
  }

  close(): void {
    this.selected.set(null);
  }

  take(order: Delivery): void {
    this.act(this.delivery.take(order.id), `Pedido #${order.id}: en camino`, 'mine');
  }

  deliver(order: Delivery): void {
    this.act(this.delivery.deliver(order.id), `Pedido #${order.id} entregado`);
  }

  release(order: Delivery): void {
    this.act(this.delivery.release(order.id), `Pedido #${order.id} vuelve a "Para llevar"`, 'ready');
  }

  /** Enlace al mapa con la direccion (abre la app de mapas del movil). */
  mapUrl(order: Delivery): string {
    return 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(order.address);
  }

  telUrl(order: Delivery): string {
    return 'tel:' + (order.customerPhone ?? '').replace(/\s/g, '');
  }

  private act(request: ReturnType<DeliveryService['take']>, message: string, goTo?: Tab): void {
    if (this.busy()) return;
    this.busy.set(true);
    request.subscribe({
      next: (updated) => {
        this.orders.update((orders) => orders.map((o) => (o.id === updated.id ? updated : o)));
        this.busy.set(false);
        this.selected.set(null);
        if (goTo) this.tab.set(goTo);
        this.notify.success(message);
      },
      error: (err) => {
        this.busy.set(false);
        this.notify.error(apiErrorMessage(err));
        this.load();
      },
    });
  }
}
