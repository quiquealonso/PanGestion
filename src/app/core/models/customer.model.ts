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
}

export interface CustomerRequest {
  name: string;
  email: string;
  phone: string | null;
  businessName: string | null;
  taxId: string | null;
}
