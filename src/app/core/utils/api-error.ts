import { HttpErrorResponse } from '@angular/common/http';

/** Convierte un error HTTP de la API en un texto legible para el usuario. */
export function apiErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'No se puede conectar con el servidor. ¿Está arrancado el backend?';
    }
    if (error.status === 403) {
      return 'No tienes permisos para realizar esta acción.';
    }
    const body = error.error as { message?: string; fieldErrors?: Record<string, string> } | null;
    const fields = body?.fieldErrors ? Object.entries(body.fieldErrors) : [];
    if (fields.length > 0) {
      return `${body?.message ?? 'Datos no válidos'}: ${fields.map(([field, msg]) => `${field} ${msg}`).join(', ')}`;
    }
    if (body?.message) {
      return body.message;
    }
  }
  return 'Ha ocurrido un error inesperado.';
}
