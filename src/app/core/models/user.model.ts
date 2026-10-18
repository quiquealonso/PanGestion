export type Role = 'SUPERADMIN' | 'ADMIN' | 'GESTOR' | 'VISOR';

export const ROLE_LABELS: Record<Role, string> = {
  SUPERADMIN: 'Superadministrador',
  ADMIN: 'Administrador',
  GESTOR: 'Gestor',
  VISOR: 'Visor',
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
