import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Store, StoreRequest, TimeSlot } from '../models/store.model';
import { CrudService } from './crud.service';

@Injectable({ providedIn: 'root' })
export class StoresService extends CrudService<Store, StoreRequest> {
  constructor() {
    super('stores');
  }

  /** Franjas de recogida de un dia (yyyy-MM-dd). */
  slots(storeId: number, date: string): Observable<TimeSlot[]> {
    return this.http.get<TimeSlot[]>(`${this.url}/${storeId}/slots`, { params: { date } });
  }
}
