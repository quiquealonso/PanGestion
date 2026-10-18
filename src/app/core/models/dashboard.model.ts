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
