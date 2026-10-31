import { HttpErrorResponse } from '@angular/common/http';

/** Convierte un error HTTP de la API en un texto legible para el usuario. */
export function apiErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'No se puede conectar con el servidor. Comprueba tu conexión e inténtalo de nuevo.';
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
    if (error.status >= 500) {
      // El codigo de la peticion (X-Request-Id) sirve para buscar el error en el log del servidor
      const code = error.headers?.get('X-Request-Id');
      return `Error del servidor${code ? ` (código ${code})` : ''}. Inténtalo de nuevo en un momento.`;
    }
  }
  return 'Ha ocurrido un error inesperado.';
}
