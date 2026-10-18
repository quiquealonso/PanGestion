import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Company } from '../../core/models/company.model';
import { ROLE_LABELS, Role, User } from '../../core/models/user.model';
import { AuthService } from '../../core/services/auth.service';
import { CompaniesService } from '../../core/services/companies.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NotificationService } from '../../core/services/notification.service';
import { UsersService } from '../../core/services/users.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { ModalComponent } from '../../shared/modal/modal.component';

/**
 * Usuarios del panel.
 * SUPERADMIN: todos los usuarios, agrupados por empresa, y elige la empresa de cada uno.
 * ADMIN: solo los de su empresa.
 */
@Component({
  imports: [DatePipe, ReactiveFormsModule, ModalComponent],
  selector: 'app-users-page',
  templateUrl: './users-page.component.html',
  styles: '.groups { display: grid; gap: 1rem; }',
})
export class UsersPageComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly usersService = inject(UsersService);
  private readonly companiesService = inject(CompaniesService);
  private readonly notify = inject(NotificationService);
  private readonly confirmService = inject(ConfirmService);
  protected readonly auth = inject(AuthService);

  readonly roleLabels = ROLE_LABELS;
  readonly users = signal<User[]>([]);
  readonly companies = signal<Company[]>([]);
  readonly selectedId = signal<number | null>(null);
  readonly dialogOpen = signal(false);
  readonly selected = computed(() => this.users().find((u) => u.id === this.selectedId()) ?? null);

  /** Roles que puede asignar quien esta logueado. */
  readonly roles = computed<Role[]>(() =>
    this.auth.isSuperAdmin() ? ['SUPERADMIN', 'ADMIN', 'GESTOR', 'VISOR'] : ['ADMIN', 'GESTOR', 'VISOR'],
  );

  /** Usuarios agrupados por empresa (los SUPERADMIN, en su propio grupo). */
  readonly groups = computed(() => {
    const groups: { name: string; users: User[] }[] = [];
    for (const user of this.users()) {
      const name = user.companyName ?? 'Superadministradores';
      let group = groups.find((g) => g.name === name);
      if (!group) {
        group = { name, users: [] };
        groups.push(group);
      }
      group.users.push(user);
    }
    return groups.sort((a, b) => a.name.localeCompare(b.name));
  });

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.minLength(6)],
    role: ['GESTOR' as Role, Validators.required],
    active: [true],
    companyId: [null as number | null],
  });

  ngOnInit(): void {
    if (this.auth.isSuperAdmin()) {
      this.companiesService.list().subscribe((companies) => this.companies.set(companies));
    }
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
    this.form.reset({ name: user.name, email: user.email, password: '', role: user.role, active: user.active, companyId: user.companyId });
    this.dialogOpen.set(true);
  }

  startCreate(): void {
    this.selectedId.set(null);
    this.form.reset({ name: '', email: '', password: '', role: 'ADMIN', active: true, companyId: this.companies()[0]?.id ?? null });
    this.dialogOpen.set(true);
  }

  closeDialog(): void {
    this.dialogOpen.set(false);
    this.selectedId.set(null);
  }

  save(): void {
    const id = this.selectedId();
    const value = this.form.getRawValue();
    if (this.form.invalid || (!id && !value.password)) {
      this.form.markAllAsTouched();
      if (!id && !value.password) this.notify.error('La contraseña es obligatoria para un usuario nuevo');
      return;
    }
    const body = { ...value, password: value.password || null, companyId: value.role === 'SUPERADMIN' ? null : value.companyId };
    const request = id ? this.usersService.update(id, body) : this.usersService.create(body);
    request.subscribe({
      next: (saved) => {
        this.notify.success(`Usuario "${saved.name}" guardado`);
        this.closeDialog();
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  async remove(): Promise<void> {
    const user = this.selected();
    if (!user) return;
    const accepted = await this.confirmService.ask({
      title: 'Eliminar usuario',
      message: `¿Seguro que quieres eliminar "${user.name}"? Esta acción no se puede deshacer.`,
    });
    if (!accepted) return;
    this.usersService.remove(user.id).subscribe({
      next: () => {
        this.notify.success('Usuario eliminado');
        this.closeDialog();
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }
}
