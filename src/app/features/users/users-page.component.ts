import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ROLE_LABELS, Role, User } from '../../core/models/user.model';
import { NotificationService } from '../../core/services/notification.service';
import { UsersService } from '../../core/services/users.service';
import { apiErrorMessage } from '../../core/utils/api-error';

/** Gestion de usuarios del panel (solo administradores). */
@Component({
  imports: [DatePipe, ReactiveFormsModule],
  selector: 'app-users-page',
  templateUrl: './users-page.component.html',
})
export class UsersPageComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly usersService = inject(UsersService);
  private readonly notify = inject(NotificationService);

  readonly roles: Role[] = ['ADMIN', 'GESTOR', 'VISOR'];
  readonly roleLabels = ROLE_LABELS;
  readonly users = signal<User[]>([]);
  readonly selectedId = signal<number | null>(null);
  readonly selected = computed(() => this.users().find((u) => u.id === this.selectedId()) ?? null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.minLength(6)],
    role: ['GESTOR' as Role, Validators.required],
    active: [true],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.usersService.list().subscribe({
      next: (users) => this.users.set(users),
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  select(user: User): void {
    this.selectedId.set(user.id);
    this.form.reset({ name: user.name, email: user.email, password: '', role: user.role, active: user.active });
  }

  startCreate(): void {
    this.selectedId.set(null);
    this.form.reset({ name: '', email: '', password: '', role: 'GESTOR', active: true });
  }

  save(): void {
    const id = this.selectedId();
    if (this.form.invalid || (!id && !this.form.value.password)) {
      this.form.markAllAsTouched();
      if (!id && !this.form.value.password) this.notify.error('La contraseña es obligatoria para un usuario nuevo');
      return;
    }
    const value = this.form.getRawValue();
    const body = { ...value, password: value.password || null };
    const request = id ? this.usersService.update(id, body) : this.usersService.create(body);
    request.subscribe({
      next: (saved) => {
        this.notify.success(`Usuario "${saved.name}" guardado`);
        this.selectedId.set(saved.id);
        this.form.patchValue({ password: '' });
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  remove(): void {
    const user = this.selected();
    if (!user || !confirm(`¿Eliminar el usuario "${user.name}"?`)) return;
    this.usersService.remove(user.id).subscribe({
      next: () => {
        this.notify.success('Usuario eliminado');
        this.startCreate();
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }
}
