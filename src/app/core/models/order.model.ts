export type OrderStatus = 'PENDIENTE' | 'EN_PROCESO' | 'LISTO' | 'ENTREGADO' | 'CANCELADO';

export const ORDER_STATUSES: OrderStatus[] = ['PENDIENTE', 'EN_PROCESO', 'LISTO', 'ENTREGADO', 'CANCELADO'];

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDIENTE: 'Pendiente',
  EN_PROCESO: 'En proceso',
  LISTO: 'Listo',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
};

export interface OrderLine {
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export interface Order {
  id: number;
  customerId: number;
  customerName: string;
  customerEmail: string;
  customerPhone: string | null;
  storeId: number;
  storeName: string;
  pickupDate: string;
  pickupTime: string;
  status: OrderStatus;
  notes: string | null;
  total: number;
  itemCount: number;
  createdAt: string;
  lines: OrderLine[];
}

export interface OrderLineRequest {
  productId: number;
  quantity: number;
}

export interface OrderRequest {
  customerId: number;
  storeId: number;
  pickupDate: string;
  pickupTime: string;
  notes: string | null;
  lines: OrderLineRequest[];
}

export interface OrderFilters {
  search?: string;
  status?: OrderStatus | '';
  storeId?: number | null;
  from?: string;
  to?: string;
}
