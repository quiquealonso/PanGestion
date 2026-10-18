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
  /** Ingredientes quitados en este pedido ("sin cebolla"). */
  removedIngredients: string[];
}

export interface Order {
  id: number;
  customerId: number;
  customerName: string;
  customerEmail: string;
  customerPhone: string | null;
  companyId: number;
  companyName: string;
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
  /** Ingredientes del producto que se quitan (opcional). */
  removedIngredientIds?: number[];
}

export interface OrderRequest {
  customerId: number;
  pickupDate: string;
  pickupTime: string;
  notes: string | null;
  lines: OrderLineRequest[];
}

export interface OrderFilters {
  search?: string;
  status?: OrderStatus | '';
  from?: string;
  to?: string;
}

/** Fila de la hoja de produccion: unidades de un producto (y su personalizacion) para una hora de recogida. */
export interface ProductionRow {
  productId: number;
  productName: string;
  abbreviation: string | null;
  categoryName: string;
  categoryColor: string;
  removedIngredients: string[];
  pickupTime: string;
  quantity: number;
}

/** "pizza carbonara (sin bacon, sin nata)" */
export function productionLabel(row: { productName: string; removedIngredients: string[] }): string {
  return row.removedIngredients.length
    ? `${row.productName} (${row.removedIngredients.map((i) => 'sin ' + i.toLowerCase()).join(', ')})`
    : row.productName;
}
