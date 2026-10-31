/** Cuenta de cliente de la web publica de pedidos. */
export interface CustomerAccount {
  id: number;
  name: string;
  email: string;
  phone: string | null;
}

export interface CustomerSession {
  token: string;
  expiresInMinutes: number;
  account: CustomerAccount;
}

/** verificationRequired = hay que abrir el enlace del email; si no, session ya trae la sesion iniciada. */
export interface RegisterResponse {
  verificationRequired: boolean;
  message: string;
  session: CustomerSession | null;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
  phone: string | null;
}
