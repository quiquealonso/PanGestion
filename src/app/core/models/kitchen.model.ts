import { Station } from './product.model';
import { ItemStatus } from './table-service.model';

/** De donde viene el producto: una mesa o un pedido para llevar. */
export type KitchenSource = 'MESA' | 'LLEVAR';

export interface KitchenItem {
  source: KitchenSource;
  id: number;
  productName: string;
  abbreviation: string | null;
  /** La categoria distingue dos productos que se llaman igual (color de la fila y su inicial). */
  categoryName: string;
  categoryColor: string | null;
  /** Posicion de la categoria en cocina (los entrantes primero) */
  categoryOrder: number;
  quantity: number;
  notes: string | null;
  removedIngredients: string[];
  /** Extras anadidos ("Huevo") */
  extras: string[];
  station: Station;
  status: ItemStatus;
  statusChangedAt: string | null;
}

/** Ticket de cocina: una ronda de una mesa o un pedido para llevar. */
export interface KitchenTicket {
  source: KitchenSource;
  refId: number;
  round: number | null;
  label: string;
  detail: string;
  notes: string | null;
  /** Cuando se pidio (mesas) o la hora de recogida (para llevar) */
  time: string;
  items: KitchenItem[];
}
