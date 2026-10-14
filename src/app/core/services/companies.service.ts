import { Injectable } from '@angular/core';
import { Company, CompanyRequest } from '../models/company.model';
import { CrudService } from './crud.service';

@Injectable({ providedIn: 'root' })
export class CompaniesService extends CrudService<Company, CompanyRequest> {
  constructor() {
    super('companies');
  }
}
