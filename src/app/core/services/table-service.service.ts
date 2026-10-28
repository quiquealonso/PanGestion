import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Bill, CashDay, FloorArea, PaymentRequest, RoundLineRequest, TableSession } from '../models/table-service.model';

/** Servicio de mesas (camareros): plano en vivo, abrir mesa, rondas y liberar. En /api/service. */
@Injectable({ providedIn: 'root' })
export class TableServiceService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/service`;

  floor(): Observable<FloorArea[]> {
    return this.http.get<FloorArea[]>(`${this.url}/floor`);
  }

  get(id: number): Observable<TableSession> {
    return this.http.get<TableSession>(`${this.url}/sessions/${id}`);
  }

  open(tableIds: number[], guests: number, notes: string | null): Observable<TableSession> {
    return this.http.post<TableSession>(`${this.url}/sessions`, { tableIds, guests, notes });
  }

  update(id: number, guests: number, notes: string | null): Observable<TableSession> {
    return this.http.put<TableSession>(`${this.url}/sessions/${id}`, { guests, notes });
  }

  changeTables(id: number, tableIds: number[]): Observable<TableSession> {
    return this.http.put<TableSession>(`${this.url}/sessions/${id}/tables`, { tableIds });
  }

  sendRound(id: number, lines: RoundLineRequest[]): Observable<TableSession> {
    return this.http.post<TableSession>(`${this.url}/sessions/${id}/rounds`, { lines });
  }

  removeLine(id: number, lineId: number): Observable<TableSession> {
    return this.http.delete<TableSession>(`${this.url}/sessions/${id}/lines/${lineId}`);
  }

  bill(id: number): Observable<Bill> {
    return this.http.get<Bill>(`${this.url}/sessions/${id}/bill`);
  }

  pay(id: number, body: PaymentRequest): Observable<TableSession> {
    return this.http.post<TableSession>(`${this.url}/sessions/${id}/payments`, body);
  }

  removePayment(id: number, paymentId: number): Observable<TableSession> {
    return this.http.delete<TableSession>(`${this.url}/sessions/${id}/payments/${paymentId}`);
  }

  /** Caja de un dia (yyyy-MM-dd). */
  day(date: string): Observable<CashDay> {
    return this.http.get<CashDay>(`${this.url}/day`, { params: { date } });
  }

  close(id: number): Observable<TableSession> {
    return this.http.post<TableSession>(`${this.url}/sessions/${id}/close`, {});
  }
}
