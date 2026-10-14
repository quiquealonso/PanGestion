import { Injectable } from '@angular/core';
import { User, UserRequest } from '../models/user.model';
import { CrudService } from './crud.service';

@Injectable({ providedIn: 'root' })
export class UsersService extends CrudService<User, UserRequest> {
  constructor() {
    super('users');
  }
}
