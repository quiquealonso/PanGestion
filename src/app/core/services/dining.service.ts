import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DiningArea, DiningAreaRequest, DiningTable, DiningTableRequest } from '../models/dining.model';

/** Plano de la sala (bares y restaurantes): zonas y mesas, en /api/dining. */
@Injectable({ providedIn: 'root' })
export class DiningService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/dining`;

  areas(): Observable<DiningArea[]> {
    return this.http.get<DiningArea[]>(`${this.url}/areas`);
  }

  createArea(body: DiningAreaRequest): Observable<DiningArea> {
    return this.http.post<DiningArea>(`${this.url}/areas`, body);
  }

  updateArea(id: number, body: DiningAreaRequest): Observable<DiningArea> {
    return this.http.put<DiningArea>(`${this.url}/areas/${id}`, body);
  }

  removeArea(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/areas/${id}`);
  }

  createTable(areaId: number, body: DiningTableRequest): Observable<DiningTable> {
    return this.http.post<DiningTable>(`${this.url}/areas/${areaId}/tables`, body);
  }

  updateTable(id: number, body: DiningTableRequest): Observable<DiningTable> {
    return this.http.put<DiningTable>(`${this.url}/tables/${id}`, body);
  }

  removeTable(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/tables/${id}`);
  }
}
