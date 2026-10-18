import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ORDER_STATUSES, Order, OrderFilters, OrderStatus, productionLabel } from '../../core/models/order.model';
import { AuthService } from '../../core/services/auth.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NotificationService } from '../../core/services/notification.service';
import { OrdersService } from '../../core/services/orders.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { addDays, toIsoDate } from '../../core/utils/dates';
import { ModalComponent } from '../../shared/modal/modal.component';
import { OrderStatusPipe } from '../../shared/order-status.pipe';

@Component({
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, OrderStatusPipe, ModalComponent],
  selector: 'app-orders-page',
  styleUrl: './orders-page.component.scss',
  templateUrl: './orders-page.component.html',
})
export class OrdersPageComponent implements OnInit {
  private readonly ordersService = inject(OrdersService);
  private readonly notify = inject(NotificationService);
  private readonly confirmService = inject(ConfirmService);
  protected readonly auth = inject(AuthService);

  /** Parametro de ruta /orders/:id y query param ?status= (withComponentInputBinding). */
  readonly id = input<string>();
  readonly status = input<OrderStatus>();

  readonly statuses = ORDER_STATUSES;
  readonly today = toIsoDate();
  readonly orders = signal<Order[]>([]);
  readonly loading = signal(false);

  /** Dia que se esta viendo (yyyy-MM-dd); vacio = todos los dias. Por defecto, hoy. */
  readonly day = signal(toIsoDate());
  filters: OrderFilters = { search: '', status: '' };

  /** Pedido abierto en el dialogo de detalle (null = cerrado). */
  readonly selectedId = signal<number | null>(null);
  readonly selectedOrder = computed(() => this.orders().find((order) => order.id === this.selectedId()) ?? null);

  /** Pedidos agrupados por dia de recogida, de la fecha mas temprana a la mas tarde (y por hora dentro de cada dia). */
  readonly groups = computed(() => this.groupByDay().sort((a, b) => a.date.localeCompare(b.date)));

  private groupByDay() {
    const groups: { date: string; label: string | null; orders: Order[]; total: number }[] = [];
    for (const order of this.orders()) {
      let group = groups.find((g) => g.date === order.pickupDate);
      if (!group) {
        group = { date: order.pickupDate, label: this.dayLabel(order.pickupDate), orders: [], total: 0 };
        groups.push(group);
      }
      group.orders.push(order);
      group.total += order.status === 'CANCELADO' ? 0 : order.total;
    }
    groups.forEach((group) => group.orders.sort((a, b) => a.pickupTime.localeCompare(b.pickupTime)));
    return groups;
  }

  readonly totalAmount = computed(() => this.orders().reduce((sum, order) => sum + order.total, 0));

  /** Pedidos pendientes del dia elegido (los que pasaria a "En proceso" el boton). */
  readonly pendingOfDay = computed(() =>
    this.day() ? this.orders().filter((o) => o.pickupDate === this.day() && o.status === 'PENDIENTE').length : 0,
  );

  ngOnInit(): void {
    this.filters.status = this.status() ?? '';
    this.selectedId.set(this.id() ? Number(this.id()) : null);
    // Si se llega filtrando por estado (desde Inicio) o a un pedido concreto, se muestran todos los dias
    if (this.status() || this.id()) this.day.set('');
    this.load();
  }

  load(): void {
    this.loading.set(true);
    const day = this.day();
    this.ordersService.search({ ...this.filters, from: day, to: day }).subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.loading.set(false);
      },
      error: (err) => {
        this.notify.error(apiErrorMessage(err));
        this.loading.set(false);
      },
    });
  }

  clearFilters(): void {
    this.filters = { search: '', status: '' };
    this.day.set(toIsoDate());
    this.load();
  }

  setDay(day: string): void {
    this.day.set(day);
    this.load();
  }

  /** Dia anterior (-1) o siguiente (+1) al que se esta viendo. */
  moveDay(delta: number): void {
    this.setDay(addDays(delta, new Date((this.day() || toIsoDate()) + 'T00:00')));
  }

  /**
   * Descarga un .txt con todo lo que hay que preparar el dia elegido: una linea "producto: cantidad" por producto
   * (las personalizaciones van aparte, por ejemplo "pizza carbonara (sin bacon): 1"). No cuenta los cancelados.
   */
  downloadDay(): void {
    const day = this.day();
    if (!day) return;
    this.ordersService.production(day, false).subscribe({
      next: (rows) => {
        if (rows.length === 0) {
          this.notify.error('No hay productos que preparar ese día');
          return;
        }
        const totals = new Map<string, number>();
        rows.forEach((row) => totals.set(productionLabel(row), (totals.get(productionLabel(row)) ?? 0) + row.quantity));
        // Saltos de linea de Windows (\r\n) para que el .txt se vea bien tambien en el Bloc de notas
        const text = [...totals].map(([name, quantity]) => `${name}: ${quantity}`).join('\r\n') + '\r\n';
        const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
        const link = document.createElement('a');
        link.href = url;
        link.download = `produccion-${day}.txt`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  /** Empieza la produccion del dia: todos sus pedidos pendientes pasan a "En proceso". */
  async startDay(): Promise<void> {
    const day = this.day();
    const count = this.pendingOfDay();
    if (!day || count === 0) return;
    const accepted = await this.confirmService.ask({
      title: 'Pasar a en proceso',
      message: `Los ${count} pedidos pendientes del ${new Date(day + 'T00:00').toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' })} pasarán a "En proceso".`,
      confirmText: 'Pasar a en proceso',
      danger: false,
    });
    if (!accepted) return;
    this.ordersService.startDay(day).subscribe({
      next: ({ updated }) => {
        this.notify.success(`${updated} ${updated === 1 ? 'pedido pasado' : 'pedidos pasados'} a "En proceso"`);
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  changeStatus(order: Order, status: OrderStatus): void {
    this.ordersService.changeStatus(order.id, status).subscribe({
      next: (updated) => {
        this.orders.update((orders) => orders.map((o) => (o.id === updated.id ? updated : o)));
        this.notify.success(`Pedido #${order.id} actualizado`);
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  async remove(order: Order): Promise<void> {
    const accepted = await this.confirmService.ask({
      title: 'Eliminar pedido',
      message: `¿Seguro que quieres eliminar el pedido #${order.id} de ${order.customerName}? Esta acción no se puede deshacer.`,
    });
    if (!accepted) return;
    this.ordersService.remove(order.id).subscribe({
      next: () => {
        this.orders.update((orders) => orders.filter((o) => o.id !== order.id));
        this.notify.success(`Pedido #${order.id} eliminado`);
        this.closeDetail();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  openDetail(order: Order): void {
    this.selectedId.set(order.id);
  }

  closeDetail(): void {
    this.selectedId.set(null);
  }

  /** "Hoy", "Mañana" o "Ayer"; para el resto de dias se muestra solo la fecha. */
  private dayLabel(date: string): string | null {
    if (date === toIsoDate()) return 'Hoy';
    if (date === addDays(1)) return 'Mañana';
    if (date === addDays(-1)) return 'Ayer';
    return null;
  }

  isEditable(order: Order): boolean {
    return order.status !== 'ENTREGADO' && order.status !== 'CANCELADO';
  }
}
