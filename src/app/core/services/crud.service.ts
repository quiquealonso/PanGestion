import { HttpClient } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Operaciones CRUD comunes contra un recurso REST (/api/<recurso>).
 * Cada servicio concreto solo indica la ruta y anade sus metodos propios.
 */
export abstract class CrudService<T, TRequest> {
  protected readonly http = inject(HttpClient);
  protected readonly url: string;

  protected constructor(resource: string) {
    this.url = `${environment.apiBaseUrl}/${resource}`;
  }

  list(): Observable<T[]> {
    return this.http.get<T[]>(this.url);
  }

  get(id: number): Observable<T> {
    return this.http.get<T>(`${this.url}/${id}`);
  }

  create(body: TRequest): Observable<T> {
    return this.http.post<T>(this.url, body);
  }

  update(id: number, body: TRequest): Observable<T> {
    return this.http.put<T>(`${this.url}/${id}`, body);
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
