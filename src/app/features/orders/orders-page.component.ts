import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ORDER_STATUSES, Order, OrderFilters, OrderStatus } from '../../core/models/order.model';
import { Store } from '../../core/models/store.model';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { OrdersService } from '../../core/services/orders.service';
import { StoresService } from '../../core/services/stores.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { OrderStatusPipe } from '../../shared/order-status.pipe';

@Component({
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, OrderStatusPipe],
  selector: 'app-orders-page',
  styleUrl: './orders-page.component.scss',
  templateUrl: './orders-page.component.html',
})
export class OrdersPageComponent implements OnInit {
  private readonly ordersService = inject(OrdersService);
  private readonly storesService = inject(StoresService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);

  /** Parametro de ruta /orders/:id y query param ?status= (withComponentInputBinding). */
  readonly id = input<string>();
  readonly status = input<OrderStatus>();

  readonly statuses = ORDER_STATUSES;
  readonly orders = signal<Order[]>([]);
  readonly stores = signal<Store[]>([]);
  readonly loading = signal(false);

  filters: OrderFilters = { search: '', status: '', storeId: null, from: '', to: '' };

  readonly selectedId = signal<number | null>(null);
  readonly selectedOrder = computed(() => this.orders().find((order) => order.id === this.selectedId()) ?? null);

  readonly totalAmount = computed(() => this.orders().reduce((sum, order) => sum + order.total, 0));

  ngOnInit(): void {
    this.filters.status = this.status() ?? '';
    this.selectedId.set(this.id() ? Number(this.id()) : null);
    this.storesService.list().subscribe((stores) => this.stores.set(stores));
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.ordersService.search(this.filters).subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.loading.set(false);
        if (!this.selectedOrder() && orders.length > 0) {
          this.selectedId.set(orders[0].id);
        }
      },
      error: (err) => {
        this.notify.error(apiErrorMessage(err));
        this.loading.set(false);
      },
    });
  }

  clearFilters(): void {
    this.filters = { search: '', status: '', storeId: null, from: '', to: '' };
    this.load();
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

  remove(order: Order): void {
    if (!confirm(`¿Eliminar el pedido #${order.id} de ${order.customerName}?`)) {
      return;
    }
    this.ordersService.remove(order.id).subscribe({
      next: () => {
        this.orders.update((orders) => orders.filter((o) => o.id !== order.id));
        this.notify.success(`Pedido #${order.id} eliminado`);
        this.selectedId.set(this.orders()[0]?.id ?? null);
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  isEditable(order: Order): boolean {
    return order.status !== 'ENTREGADO' && order.status !== 'CANCELADO';
  }
}
