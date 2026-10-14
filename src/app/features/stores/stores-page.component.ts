import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Store } from '../../core/models/store.model';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { StoresService } from '../../core/services/stores.service';
import { apiErrorMessage } from '../../core/utils/api-error';

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-stores-page',
  templateUrl: './stores-page.component.html',
})
export class StoresPageComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly storesService = inject(StoresService);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);

  readonly stores = signal<Store[]>([]);
  readonly selectedId = signal<number | null>(null);
  readonly selected = computed(() => this.stores().find((s) => s.id === this.selectedId()) ?? null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    address: ['', [Validators.required, Validators.maxLength(200)]],
    phone: [''],
    openingTime: ['08:00', Validators.required],
    closingTime: ['14:00', Validators.required],
    slotMinutes: [30, [Validators.required, Validators.min(10), Validators.max(240)]],
    ordersPerSlot: [5, [Validators.required, Validators.min(1), Validators.max(100)]],
    active: [true],
  });

  ngOnInit(): void {
    if (!this.auth.canEdit()) this.form.disable();
    this.load();
  }

  load(): void {
    this.storesService.list().subscribe({
      next: (stores) => this.stores.set(stores),
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  select(store: Store): void {
    this.selectedId.set(store.id);
    this.form.reset({ ...store, phone: store.phone ?? '' });
  }

  startCreate(): void {
    this.selectedId.set(null);
    this.form.reset({ name: '', address: '', phone: '', openingTime: '08:00', closingTime: '14:00', slotMinutes: 30, ordersPerSlot: 5, active: true });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const body = { ...value, phone: value.phone || null };
    const id = this.selectedId();
    const request = id ? this.storesService.update(id, body) : this.storesService.create(body);
    request.subscribe({
      next: (saved) => {
        this.notify.success(`Tienda "${saved.name}" guardada`);
        this.selectedId.set(saved.id);
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  remove(): void {
    const store = this.selected();
    if (!store || !confirm(`¿Eliminar la tienda "${store.name}"?`)) return;
    this.storesService.remove(store.id).subscribe({
      next: () => {
        this.notify.success('Tienda eliminada');
        this.startCreate();
        this.load();
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }
}
