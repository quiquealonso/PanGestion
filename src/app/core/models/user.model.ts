export type Role = 'ADMIN' | 'GESTOR' | 'VISOR';

export const ROLE_LABELS: Record<Role, string> = {
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
}

export interface UserRequest {
  name: string;
  email: string;
  /** Vacia al editar = no cambiar. */
  password: string | null;
  role: Role;
  active: boolean;
}

export interface LoginResponse {
  token: string;
  expiresInMinutes: number;
  user: User;
}
