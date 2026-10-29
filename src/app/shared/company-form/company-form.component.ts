import { Component, effect, inject, input, output } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BUSINESS_TYPES, BUSINESS_TYPE_LABELS, BusinessType, Company, CompanyRequest } from '../../core/models/company.model';

/**
 * Formulario de los datos de una empresa (panaderia): datos fiscales, contacto, direccion y horario de recogida.
 * Lo usan "Empresas" (SUPERADMIN) y "Mi empresa" (ADMIN).
 */
@Component({
  selector: 'app-company-form',
  imports: [ReactiveFormsModule],
  template: `
    <form [formGroup]="form" class="form-grid" (ngSubmit)="submit()">
      <label><span>Nombre del negocio</span><input formControlName="name" /></label>
      <label>
        <span>Tipo de negocio</span>
        <select formControlName="type">
          @for (type of types; track type) {
            <option [value]="type">{{ typeLabels[type] }}</option>
          }
        </select>
      </label>
      <label><span>CIF / NIF</span><input formControlName="taxId" /></label>
      <label><span>Teléfono</span><input formControlName="phone" /></label>
      <label class="full"><span>Email</span><input type="email" formControlName="email" /></label>
      <label class="full"><span>Dirección (punto de recogida)</span><input formControlName="address" /></label>

      <p class="eyebrow full section">Horario de recogida</p>
      <label><span>Apertura</span><input type="time" formControlName="openingTime" /></label>
      <label><span>Cierre</span><input type="time" formControlName="closingTime" /></label>
      <label><span>Minutos por franja</span><input type="number" min="10" max="240" formControlName="slotMinutes" /></label>
      <label><span>Pedidos por franja</span><input type="number" min="1" max="100" formControlName="ordersPerSlot" /></label>
      <label class="full check"><input type="checkbox" formControlName="active" /> Acepta pedidos (aparece en la web pública)</label>
      <label class="full check"><input type="checkbox" formControlName="delivery" /> Hace envíos a domicilio (los pedidos pueden ser «a domicilio» y los repartidores los llevan)</label>

      @if (!readonly()) {
        <div class="form-actions full">
          <button type="submit" class="btn btn--primary">Guardar</button>
          <ng-content />
        </div>
      }
    </form>
  `,
  styles: '.section { margin: 0.6rem 0 0; }',
})
export class CompanyFormComponent {
  private readonly fb = inject(FormBuilder);

  /** Empresa a editar; null = alta de una nueva. */
  readonly company = input<Company | null>(null);
  readonly readonly = input(false);
  /** Solo el SUPERADMIN cambia el tipo de negocio. */
  readonly canEditType = input(false);
  readonly types = BUSINESS_TYPES;
  readonly typeLabels = BUSINESS_TYPE_LABELS;
  readonly submitted = output<CompanyRequest>();

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    type: ['PANADERIA' as BusinessType, Validators.required],
    taxId: ['', [Validators.required, Validators.maxLength(20)]],
    email: ['', Validators.email],
    phone: [''],
    address: ['', [Validators.required, Validators.maxLength(200)]],
    openingTime: ['08:00', Validators.required],
    closingTime: ['14:00', Validators.required],
    slotMinutes: [30, [Validators.required, Validators.min(10), Validators.max(240)]],
    ordersPerSlot: [5, [Validators.required, Validators.min(1), Validators.max(100)]],
    active: [true],
    delivery: [false],
  });

  constructor() {
    // Rellena el formulario cada vez que cambia la empresa recibida
    effect(() => {
      const company = this.company();
      this.form.reset(
        company
          ? { ...company, email: company.email ?? '', phone: company.phone ?? '' }
          : { name: '', type: 'PANADERIA' as BusinessType, taxId: '', email: '', phone: '', address: '', openingTime: '08:00', closingTime: '14:00', slotMinutes: 30, ordersPerSlot: 5, active: true, delivery: false },
      );
      if (this.readonly()) this.form.disable();
      else this.form.enable();
      if (!this.canEditType()) this.form.controls.type.disable();
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.submitted.emit({ ...value, email: value.email || null, phone: value.phone || null });
  }
}
