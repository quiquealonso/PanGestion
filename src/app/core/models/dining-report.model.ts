import { Station } from './product.model';

/** Informe de sala de un periodo (bares y restaurantes). */
export interface DiningReport {
  from: string;
  to: string;
  revenue: number;
  cash: number;
  card: number;
  tables: number;
  guests: number;
  avgTicket: number;
  avgPerGuest: number;
  /** Minutos medios que dura una mesa */
  avgMinutes: number;
  byDay: { date: string; tables: number; guests: number; revenue: number }[];
  byHour: { hour: number; tables: number; revenue: number }[];
  topProducts: { name: string; quantity: number; revenue: number }[];
  byWaiter: { name: string; tables: number; guests: number; revenue: number; avgTicket: number }[];
  byTable: { table: string; uses: number; revenue: number }[];
  /** Minutos de pedido a listo por estacion */
  kitchen: { station: Station; items: number; avgMinutes: number; maxMinutes: number }[];
}
