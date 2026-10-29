/** Tipo de negocio: permite en el futuro funciones propias de cada tipo. */
export type BusinessType = 'PANADERIA' | 'BAR' | 'RESTAURANTE';

export const BUSINESS_TYPES: BusinessType[] = ['PANADERIA', 'BAR', 'RESTAURANTE'];

export const BUSINESS_TYPE_LABELS: Record<BusinessType, string> = {
  PANADERIA: 'Panadería',
  BAR: 'Bar',
  RESTAURANTE: 'Restaurante',
};

/** Empresa (panaderia, bar o restaurante) que usa la app. Tambien es el punto de recogida de sus pedidos. */
export interface Company {
  id: number;
  name: string;
  type: BusinessType;
  taxId: string;
  email: string | null;
  phone: string | null;
  address: string;
  openingTime: string;
  closingTime: string;
  /** Duracion de cada franja de recogida en minutos. */
  slotMinutes: number;
  /** Pedidos maximos por franja. */
  ordersPerSlot: number;
  /** false = no acepta pedidos ni sale en la web publica. */
  active: boolean;
  /** Hace envios a domicilio (la web ofrece "a domicilio" ademas de "recoger"). */
  delivery: boolean;
  userCount: number;
  customerCount: number;
  /** Logo (/uploads/...); null = sin logo. Se sube aparte (POST /api/company/logo). */
  logoUrl: string | null;
}

export type CompanyRequest = Omit<Company, 'id' | 'userCount' | 'customerCount' | 'logoUrl'>;

/** Franja de recogida de un dia. */
export interface TimeSlot {
  time: string;
  available: boolean;
  remaining: number;
}
