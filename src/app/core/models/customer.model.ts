/** Cliente de la panaderia. Si es un negocio, guarda razon social y CIF. */
export interface Customer {
  id: number;
  name: string;
  email: string;
  phone: string | null;
  /** null = particular */
  businessName: string | null;
  taxId: string | null;
  createdAt: string;
  /** Resumen de sus pedidos no cancelados */
  orderCount: number;
  totalSpent: number;
  /** Fecha de recogida de su ultimo pedido; null si no ha pedido nunca */
  lastOrderDate: string | null;
}

export interface CustomerRequest {
  name: string;
  email: string;
  phone: string | null;
  businessName: string | null;
  taxId: string | null;
}
