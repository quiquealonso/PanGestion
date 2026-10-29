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

  /** Sube o sustituye el logo (JPG, PNG o WEBP de hasta 2 MB). Solo ADMIN. */
  uploadLogo(file: File): Observable<Company> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<Company>(`${this.url}/logo`, body);
  }

  removeLogo(): Observable<Company> {
    return this.http.delete<Company>(`${this.url}/logo`);
  }

  /** Franjas de recogida de un dia (yyyy-MM-dd). */
  slots(date: string): Observable<TimeSlot[]> {
    return this.http.get<TimeSlot[]>(`${this.url}/slots`, { params: { date } });
  }
}
