import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Delivery } from '../models/delivery.model';

/** Reparto a domicilio: pedidos de hoy y "me lo llevo / entregado". En /api/delivery. */
@Injectable({ providedIn: 'root' })
export class DeliveryService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/delivery`;

  today(): Observable<Delivery[]> {
    return this.http.get<Delivery[]>(this.url);
  }

  /** Me lo llevo: pasa a Transportando a mi nombre. */
  take(id: number): Observable<Delivery> {
    return this.http.post<Delivery>(`${this.url}/${id}/take`, {});
  }

  /** Entregado al cliente. */
  deliver(id: number): Observable<Delivery> {
    return this.http.post<Delivery>(`${this.url}/${id}/deliver`, {});
  }

  /** No me lo llevo: vuelve a listo para que lo coja otro. */
  release(id: number): Observable<Delivery> {
    return this.http.post<Delivery>(`${this.url}/${id}/release`, {});
  }
}
