import { LineExtra } from './ingredient.model';

export type OrderStatus = 'PENDIENTE' | 'EN_PROCESO' | 'LISTO' | 'TRANSPORTANDO' | 'ENTREGADO' | 'CANCELADO';

export const ORDER_STATUSES: OrderStatus[] = ['PENDIENTE', 'EN_PROCESO', 'LISTO', 'TRANSPORTANDO', 'ENTREGADO', 'CANCELADO'];

/** Recoger en el local o a domicilio (lo lleva un repartidor). */
export type OrderType = 'RECOGER' | 'DOMICILIO';

export const ORDER_TYPE_LABELS: Record<OrderType, string> = {
  RECOGER: 'Para recoger',
  DOMICILIO: 'A domicilio',
};

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDIENTE: 'Pendiente',
  EN_PROCESO: 'En proceso',
  LISTO: 'Listo',
  TRANSPORTANDO: 'Transportando',
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
  /** Extras anadidos ("con huevo"), ya sumados en unitPrice. */
  extras: LineExtra[];
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
  type: OrderType;
  /** Solo a domicilio */
  deliveryAddress: string | null;
  /** Repartidor que lo lleva (a domicilio) */
  driverName: string | null;
}

export interface OrderLineRequest {
  productId: number;
  quantity: number;
  /** Ingredientes del producto que se quitan (opcional). */
  removedIngredientIds?: number[];
  /** Extras que se anaden, con su precio (opcional). */
  addedIngredientIds?: number[];
}

export interface OrderRequest {
  customerId: number;
  pickupDate: string;
  pickupTime: string;
  notes: string | null;
  lines: OrderLineRequest[];
  /** RECOGER por defecto; DOMICILIO necesita deliveryAddress */
  type?: OrderType;
  deliveryAddress?: string | null;
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
  /** Extras anadidos ("Huevo") */
  extras: string[];
  pickupTime: string;
  quantity: number;
}

/** "pizza carbonara (con huevo, sin bacon, sin nata)" */
export function productionLabel(row: { productName: string; removedIngredients: string[]; extras?: string[] }): string {
  const custom = [
    ...(row.extras ?? []).map((e) => 'con ' + e.toLowerCase()),
    ...row.removedIngredients.map((i) => 'sin ' + i.toLowerCase()),
  ];
  return custom.length ? `${row.productName} (${custom.join(', ')})` : row.productName;
}

/** Respuesta al hacer un pedido en la web publica: sin los datos guardados del cliente. */
export type PublicOrder = Pick<
  Order,
  'id' | 'customerName' | 'companyName' | 'pickupDate' | 'pickupTime' | 'status' | 'total' | 'itemCount' | 'lines' | 'type' | 'deliveryAddress'
>;
