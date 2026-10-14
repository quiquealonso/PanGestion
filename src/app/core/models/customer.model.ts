export interface Customer {
  id: number;
  name: string;
  email: string;
  phone: string | null;
  /** null = particular */
  companyId: number | null;
  companyName: string | null;
  createdAt: string;
}

export interface CustomerRequest {
  name: string;
  email: string;
  phone: string | null;
  companyId: number | null;
}
