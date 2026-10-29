import { BusinessType } from './company.model';

export type Role = 'SUPERADMIN' | 'ADMIN' | 'GESTOR' | 'VISOR' | 'CAMARERO' | 'COCINA' | 'REPARTIDOR';

export const ROLE_LABELS: Record<Role, string> = {
  SUPERADMIN: 'Superadministrador',
  ADMIN: 'Administrador',
  GESTOR: 'Gestor',
  VISOR: 'Visor',
  CAMARERO: 'Camarero',
  COCINA: 'Cocina',
  REPARTIDOR: 'Repartidor',
};

export interface User {
  id: number;
  name: string;
  email: string;
  role: Role;
  active: boolean;
  lastLogin: string | null;
  /** Empresa del usuario; null solo para SUPERADMIN. */
  companyId: number | null;
  companyName: string | null;
  /** Tipo de negocio de su empresa (bar y restaurante tienen plano de mesas); null para SUPERADMIN. */
  companyType: BusinessType | null;
  /** Logo de su empresa (/uploads/...) para la cabecera; null = sin logo. Puede faltar en sesiones guardadas antes del logo. */
  companyLogoUrl?: string | null;
}

export interface UserRequest {
  name: string;
  email: string;
  /** Vacia al editar = no cambiar. */
  password: string | null;
  role: Role;
  active: boolean;
  /** Solo lo usa el SUPERADMIN; un ADMIN siempre crea usuarios de su empresa. */
  companyId: number | null;
}

export interface LoginResponse {
  token: string;
  expiresInMinutes: number;
  user: User;
}
