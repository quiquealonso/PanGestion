import { Ingredient } from './ingredient.model';
import { Order } from './order.model';

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
  latestOrders: Order[];
  topProducts: { name: string; units: number }[];
  lowStockIngredients: Ingredient[];
}
