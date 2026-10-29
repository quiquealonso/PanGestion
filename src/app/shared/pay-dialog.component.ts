import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Bill, PaymentMethod, TableSession } from '../core/models/table-service.model';
import { NotificationService } from '../core/services/notification.service';
import { TableServiceService } from '../core/services/table-service.service';
import { apiErrorMessage } from '../core/utils/api-error';
import { ModalComponent } from './modal/modal.component';

type PayMode = 'total' | 'guest' | 'parts';

/**
 * Cobro de una mesa (de una vez, entre comensales o a partes iguales; en efectivo o con tarjeta, con el cambio).
 * Lo usan "Barra y cobros" y la comanda de la mesa del camarero. Al abrirse pide la cuenta (`/bill`);
 * al cobrar avisa con la mesa actualizada (cerrada si queda pagada y "Liberar la mesa" esta marcado).
 */
@Component({
  selector: 'app-pay-dialog',
  imports: [CurrencyPipe, FormsModule, ModalComponent],
  template: `
    @if (bill(); as b) {
      <app-modal [title]="'Cobrar mesa ' + session().tableLabel" width="480px" (closed)="closed.emit()">
        <div class="pay">
          <div class="segmented" role="group" aria-label="Cómo se paga">
            <button type="button" [class.on]="mode() === 'total'" (click)="mode.set('total')">Todo</button>
            <button type="button" [class.on]="mode() === 'guest'" (click)="mode.set('guest')">Entre comensales</button>
            <button type="button" [class.on]="mode() === 'parts'" (click)="mode.set('parts')">A partes iguales</button>
          </div>

          @if (mode() === 'guest') {
            <div class="guest-list">
              @for (g of b.guests; track g.guest) {
                <button type="button" class="guest-row" [class.on]="guest() === g.guest" [disabled]="g.paid" (click)="guest.set(g.guest)">
                  <span>Comensal {{ g.guest }}</span>
                  <strong>{{ g.paid ? 'Pagado' : (g.amount | currency: 'EUR') }}</strong>
                </button>
              }
            </div>
            <p class="muted small">El total a partes iguales entre los {{ b.guests.length }} comensales; cada uno queda marcado al pagar.</p>
          } @else if (mode() === 'parts') {
            <div class="field-row">
              <span>Partes de lo pendiente</span>
              <div class="stepper">
                <button type="button" (click)="changeParts(-1)" [disabled]="parts() <= 2">−</button>
                <strong>{{ parts() }}</strong>
                <button type="button" (click)="changeParts(1)" [disabled]="parts() >= 20">+</button>
              </div>
            </div>
          }

          <p class="pay__amount">{{ amount() | currency: 'EUR' }}</p>

          <div class="segmented" role="group" aria-label="Forma de pago">
            <button type="button" [class.on]="method() === 'EFECTIVO'" (click)="method.set('EFECTIVO')">💶 Efectivo</button>
            <button type="button" [class.on]="method() === 'TARJETA'" (click)="method.set('TARJETA'); cashGiven.set(null)">💳 Tarjeta</button>
          </div>

          @if (method() === 'EFECTIVO') {
            <div class="cash-row">
              <button type="button" class="chip" [class.on]="cashGiven() === amount()" (click)="cashGiven.set(amount())">Exacto</button>
              @for (note of cashOptions(); track note) {
                <button type="button" class="chip" [class.on]="cashGiven() === note" (click)="cashGiven.set(note)">{{ note }} €</button>
              }
              <input type="number" min="0" step="0.01" placeholder="Entregado" aria-label="Efectivo entregado"
                     [ngModel]="cashGiven()" (ngModelChange)="cashGiven.set($event === '' || $event === null ? null : +$event)" />
            </div>
            @if (change() !== null) {
              <p class="change" [class.change--bad]="change()! < 0">
                {{ change()! < 0 ? 'Falta ' + (-change()! | currency: 'EUR') : 'Cambio: ' + (change()! | currency: 'EUR') }}
              </p>
            }
          }

          <label class="check"><input type="checkbox" [(ngModel)]="closeWhenPaid" /> Liberar la mesa si queda pagada</label>
          <button type="button" class="btn btn--primary" [disabled]="!canPay() || saving()" (click)="pay()">Cobrar {{ amount() | currency: 'EUR' }}</button>
        </div>
      </app-modal>
    }
  `,
  styles: `
    .pay {
      display: grid;
      gap: 0.9rem;

      .segmented {
        justify-self: stretch;

        button {
          flex: 1;
        }
      }
    }

    .small {
      font-size: 0.82rem;
    }

    .pay__amount {
      margin: 0;
      font-family: 'Space Grotesk', sans-serif;
      font-size: 2.2rem;
      font-weight: 700;
      text-align: center;
    }

    .guest-list {
      display: grid;
      gap: 0.35rem;
    }

    .guest-row {
      display: flex;
      justify-content: space-between;
      padding: 0.6rem 0.8rem;
      border-radius: 0.8rem;
      border: 1px solid var(--border);
      background: rgba(var(--tint-rgb), 0.03);
      color: var(--text);
      font: inherit;
      cursor: pointer;

      &.on {
        border-color: rgba(var(--primary-rgb), 0.55);
        background: rgba(var(--primary-rgb), 0.16);
      }

      &:disabled {
        opacity: 0.5;
        cursor: default;
      }
    }

    .field-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.6rem;
    }

    .cash-row {
      display: flex;
      flex-wrap: wrap;
      gap: 0.4rem;

      input {
        flex: 1;
        min-width: 7rem;
      }
    }

    .change {
      margin: 0;
      color: var(--success-text);
      font-size: 1.15rem;
      font-weight: 700;
      text-align: center;

      &--bad {
        color: var(--danger-text);
      }
    }

    .check {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
  `,
})
export class PayDialogComponent implements OnInit {
  private readonly service = inject(TableServiceService);
  private readonly notify = inject(NotificationService);

  /** Mesa que se cobra */
  readonly session = input.required<TableSession>();
  /** Cobro hecho: la mesa como queda (CLOSED si se ha liberado) */
  readonly paid = output<TableSession>();
  readonly closed = output<void>();

  readonly bill = signal<Bill | null>(null);
  readonly mode = signal<PayMode>('total');
  readonly method = signal<PaymentMethod>('EFECTIVO');
  readonly guest = signal<number | null>(null);
  readonly parts = signal(2);
  readonly cashGiven = signal<number | null>(null);
  readonly saving = signal(false);
  closeWhenPaid = true;

  readonly amount = computed(() => {
    const bill = this.bill();
    if (!bill) return 0;
    if (this.mode() === 'total') return bill.pending;
    if (this.mode() === 'guest') return bill.guests.find((g) => g.guest === this.guest())?.amount ?? 0;
    return Math.min(bill.pending, Math.round((bill.pending / this.parts()) * 100) / 100);
  });
  readonly change = computed(() => {
    const given = this.cashGiven();
    return this.method() === 'EFECTIVO' && given !== null ? Math.round((given - this.amount()) * 100) / 100 : null;
  });
  /** Importes tipicos que entrega el cliente: el total redondeado hacia arriba a 5, 10, 20, 50 o 100 */
  readonly cashOptions = computed(() => {
    const amount = this.amount();
    const options = [5, 10, 20, 50, 100].map((step) => Math.ceil(amount / step) * step).filter((v) => v > amount);
    return [...new Set(options)].slice(0, 3);
  });
  readonly canPay = computed(() => {
    const change = this.change();
    return this.amount() > 0 && (change === null || change >= 0);
  });

  ngOnInit(): void {
    const session = this.session();
    this.service.bill(session.id).subscribe({
      next: (bill) => {
        this.guest.set(bill.guests.find((g) => !g.paid)?.guest ?? null);
        this.parts.set(Math.max(2, session.guests));
        this.bill.set(bill);
      },
      error: (err) => {
        this.notify.error(apiErrorMessage(err));
        this.closed.emit();
      },
    });
  }

  changeParts(delta: number): void {
    this.parts.set(Math.min(20, Math.max(2, this.parts() + delta)));
  }

  pay(): void {
    if (!this.canPay() || this.saving()) return;
    const mode = this.mode();
    const label = mode === 'total' ? 'Total' : mode === 'parts' ? `Parte de ${this.parts()}` : null;
    this.saving.set(true);
    this.service
      .pay(this.session().id, {
        amount: this.amount(),
        method: this.method(),
        cashGiven: this.method() === 'EFECTIVO' ? this.cashGiven() : null,
        guest: mode === 'guest' ? this.guest() : null,
        label,
        closeWhenPaid: this.closeWhenPaid,
      })
      .subscribe({
        next: (updated) => {
          this.saving.set(false);
          const change = this.change();
          const changeText = change ? ` · cambio ${euros(change)}` : '';
          if (updated.status === 'CLOSED') this.notify.success(`Mesa ${updated.tableLabel} cobrada y libre${changeText}`);
          else this.notify.success(`Cobrados ${euros(updated.paid)} de ${euros(updated.total)}${changeText}`);
          this.paid.emit(updated);
        },
        error: (err) => {
          this.saving.set(false);
          this.notify.error(apiErrorMessage(err));
        },
      });
  }
}

function euros(value: number): string {
  return `${value.toFixed(2).replace('.', ',')} €`;
}
