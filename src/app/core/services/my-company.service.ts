import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Company, CompanyRequest, TimeSlot } from '../models/company.model';

/** La empresa del usuario logueado: sus datos, horario y franjas de recogida. */
@Injectable({ providedIn: 'root' })
export class MyCompanyService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/company`;

  get(): Observable<Company> {
    return this.http.get<Company>(this.url);
  }

  update(body: CompanyRequest): Observable<Company> {
    return this.http.put<Company>(this.url, body);
  }

  /** Franjas de recogida de un dia (yyyy-MM-dd). */
  slots(date: string): Observable<TimeSlot[]> {
    return this.http.get<TimeSlot[]>(`${this.url}/slots`, { params: { date } });
  }
}
