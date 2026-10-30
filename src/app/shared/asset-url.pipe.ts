import { Pipe, PipeTransform } from '@angular/core';
import { environment } from '../../environments/environment';

/** "/uploads/..." -> URL completa en el servidor de la API (en produccion el frontend esta en otro dominio). */
@Pipe({ name: 'assetUrl' })
export class AssetUrlPipe implements PipeTransform {
  transform(url: string | null | undefined): string | null {
    if (!url) return null;
    return url.startsWith('/') ? environment.apiOrigin + url : url;
  }
}
