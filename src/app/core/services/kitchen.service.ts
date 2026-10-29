import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { KitchenSource, KitchenTicket } from '../models/kitchen.model';
import { Station } from '../models/product.model';
import { ItemStatus } from '../models/table-service.model';

/** Pantalla de cocina y barra: tickets y cambio de estado de cada producto. En /api/kitchen. */
@Injectable({ providedIn: 'root' })
export class KitchenService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/kitchen`;

  tickets(station: Station): Observable<KitchenTicket[]> {
    return this.http.get<KitchenTicket[]>(this.url, { params: { station } });
  }

  /** Pasa uno o varios productos al mismo estado. units = cuantas unidades de la linea (sin indicar: todas). */
  move(items: { source: KitchenSource; id: number; units?: number }[], status: ItemStatus): Observable<void> {
    return this.http.patch<void>(`${this.url}/items`, { items, status });
  }
}
