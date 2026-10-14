export interface Company {
  id: number;
  name: string;
  taxId: string;
  email: string | null;
  phone: string | null;
  address: string | null;
  customerCount: number;
}

export type CompanyRequest = Omit<Company, 'id' | 'customerCount'>;
