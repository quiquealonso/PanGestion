import { LineExtra } from './ingredient.model';
import { TableShape } from './dining.model';
import { Station } from './product.model';

/** Estado de cada producto pedido, por separado. */
export type ItemStatus = 'PENDIENTE' | 'PREPARANDO' | 'LISTO' | 'SERVIDO';

export const ITEM_STATUS_LABELS: Record<ItemStatus, string> = {
  PENDIENTE: 'Pendiente',
  PREPARANDO: 'Preparando',
  LISTO: 'Listo',
  SERVIDO: 'Servido',
};

export type SessionStatus = 'OPEN' | 'CLOSED';

/** Mesa ocupada, resumida para pintarla en el plano. */
export interface SessionSummary {
  id: number;
  tableLabel: string;
  guests: number;
  itemCount: number;
  total: number;
  /** Ya cobrado */
  paid: number;
  openedAt: string;
  /** Unidades listas en cocina o barra para llevar a la mesa */
  readyCount: number;
}

/** Mesa del plano en vivo; session null = libre. */
export interface FloorTable {
  id: number;
  name: string;
  seats: number;
  shape: TableShape;
  x: number;
  y: number;
  width: number;
  height: number;
  session: SessionSummary | null;
}

export interface FloorArea {
  id: number;
  name: string;
  width: number;
  height: number;
  tables: FloorTable[];
}

/** Producto pedido en la mesa (de toda la mesa: no se apunta de que comensal es). */
export interface ServiceLine {
  id: number;
  productId: number;
  productName: string;
  color: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
  notes: string | null;
  removedIngredients: string[];
  /** Extras anadidos (ya sumados en unitPrice) */
  extras: LineExtra[];
  round: number;
  createdAt: string;
  station: Station;
  status: ItemStatus;
  statusChangedAt: string | null;
}

/** Servicio de una mesa (o varias juntas): comensales y lo que se ha pedido. */
export interface TableSession {
  id: number;
  status: SessionStatus;
  tableLabel: string;
  tables: { id: number; name: string; areaId: number }[];
  guests: number;
  notes: string | null;
  waiterName: string | null;
  rounds: number;
  itemCount: number;
  total: number;
  paid: number;
  pending: number;
  openedAt: string;
  closedAt: string | null;
  lines: ServiceLine[];
  payments: Payment[];
}

export type PaymentMethod = 'EFECTIVO' | 'TARJETA';

/** Cobro de una mesa (el total o una parte). */
export interface Payment {
  id: number;
  amount: number;
  method: PaymentMethod;
  /** Comensal que pago su parte; null = pago general */
  guest: number | null;
  label: string;
  cashGiven: number | null;
  change: number;
  cashierName: string | null;
  paidAt: string;
}

export interface PaymentRequest {
  amount: number;
  method: PaymentMethod;
  cashGiven: number | null;
  guest: number | null;
  label: string | null;
  /** Liberar la mesa si con este pago queda pagada */
  closeWhenPaid: boolean;
}

/** Cuenta para cobrar, con la parte de cada comensal (el total a partes iguales). */
export interface Bill {
  sessionId: number;
  total: number;
  paid: number;
  pending: number;
  guests: { guest: number; amount: number; paid: boolean }[];
  payments: Payment[];
}

/** Mesa ya cerrada (caja del dia). */
export interface ClosedSession {
  id: number;
  tableLabel: string;
  guests: number;
  itemCount: number;
  total: number;
  paid: number;
  waiterName: string | null;
  openedAt: string;
  closedAt: string;
}

/** Caja de un dia. */
export interface CashDay {
  date: string;
  cash: number;
  card: number;
  total: number;
  tables: number;
  sessions: ClosedSession[];
}

export interface RoundLineRequest {
  productId: number;
  quantity: number;
  notes: string | null;
  removedIngredientIds: number[];
  addedIngredientIds: number[];
}
