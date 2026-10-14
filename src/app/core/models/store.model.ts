export interface Store {
  id: number;
  name: string;
  address: string;
  phone: string | null;
  openingTime: string;
  closingTime: string;
  slotMinutes: number;
  ordersPerSlot: number;
  active: boolean;
}

export type StoreRequest = Omit<Store, 'id'>;

export interface TimeSlot {
  time: string;
  available: boolean;
  remaining: number;
}
