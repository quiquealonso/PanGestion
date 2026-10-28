export interface Dashboard {
  pendingOrders: number;
  inProcessOrders: number;
  readyOrders: number;
  todayOrders: number;
  customers: number;
  activeProducts: number;
  monthRevenue: number;
  monthOrders: number;
  averageTicket: number;
  topProducts: { name: string; units: number }[];
}

/** Unidades vendidas por dia (lunes a domingo) de una semana y de la anterior. */
export interface WeeklySales {
  /** Lunes de la semana (yyyy-MM-dd). */
  weekStart: string;
  current: number[];
  previous: number[];
}

/** Inicio de un bar o restaurante: la sala ahora mismo y la caja de hoy. */
export interface DiningDashboard {
  tables: number;
  occupiedTables: number;
  openSessions: number;
  guestsNow: number;
  /** Unidades listas para llevar a las mesas */
  readyItems: number;
  kitchenQueue: number;
  barQueue: number;
  /** Pedidos para llevar de hoy sin entregar */
  takeawayToday: number;
  revenueToday: number;
  cashToday: number;
  cardToday: number;
  closedToday: number;
  avgTicketToday: number;
}
