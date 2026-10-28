import { OrderStatus } from './order.model';

/** Pedido a domicilio en la pantalla del repartidor. */
export interface Delivery {
  id: number;
  status: OrderStatus;
  customerName: string;
  customerPhone: string | null;
  address: string;
  date: string;
  /** Hora a la que el cliente quiere recibirlo */
  time: string;
  notes: string | null;
  total: number;
  itemCount: number;
  /** "2× Barbacoa (con huevo, sin cebolla)" */
  items: string[];
  driverName: string | null;
  /** Lo lleva quien esta mirando */
  mine: boolean;
  dispatchedAt: string | null;
}
