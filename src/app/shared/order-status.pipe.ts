import { Pipe, PipeTransform } from '@angular/core';
import { ORDER_STATUS_LABELS, OrderStatus } from '../core/models/order.model';

/** PENDIENTE -> "Pendiente", EN_PROCESO -> "En proceso"... */
@Pipe({ name: 'orderStatus' })
export class OrderStatusPipe implements PipeTransform {
  transform(status: OrderStatus): string {
    return ORDER_STATUS_LABELS[status] ?? status;
  }
}
