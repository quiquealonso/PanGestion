import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Company } from '../../core/models/company.model';
import {
  Bill,
  CashDay,
  FloorArea,
  FloorTable,
  ITEM_STATUS_LABELS,
  Payment,
  PaymentMethod,
  ServiceLine,
  TableSession,
} from '../../core/models/table-service.model';
import { ConfirmService } from '../../core/services/confirm.service';
import { LiveEventsService } from '../../core/services/live-events.service';
import { MyCompanyService } from '../../core/services/my-company.service';
import { NotificationService } from '../../core/services/notification.service';
import { TableServiceService } from '../../core/services/table-service.service';
import { apiErrorMessage } from '../../core/utils/api-error';
import { DiningTableComponent } from '../../shared/dining-table.component';
import { customizationText } from '../../shared/extras-picker.component';
import { ModalComponent } from '../../shared/modal/modal.component';

/** Respaldo por si se corta la conexion de avisos en tiempo real. */
const REFRESH_MS = 60_000;

type PayMode = 'total' | 'guest' | 'parts';

/** Linea de la precuenta: mismo producto y precio, juntos. */
interface ReceiptLine {
  name: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

/**
 * Barra: plano en vivo para ver todas las mesas, cobrarlas (de una vez, por comensal o a partes iguales,
 * en efectivo o con tarjeta), imprimir la precuenta y ver la caja del dia.
 * La precuenta no es una factura: no sustituye al ticket fiscal de la caja registradora.
 */
@Component({
  imports: [CurrencyPipe, DatePipe, FormsModule, DiningTableComponent, ModalComponent],
  selector: 'app-bar-page',
  templateUrl: './bar-page.component.html',
  styleUrl: './bar-page.component.scss',
})
export class BarPageComponent implements OnInit {
  private readonly service = inject(TableServiceService);
  private readonly companyService = inject(MyCompanyService);
  private readonly notify = inject(NotificationService);
  private readonly confirmService = inject(ConfirmService);
  private readonly live = inject(LiveEventsService);
  private readonly destroyRef = inject(DestroyRef);

  readonly statusLabels = ITEM_STATUS_LABELS;
  readonly areas = signal<FloorArea[]>([]);
  readonly activeAreaId = signal<number | null>(null);
  readonly activeArea = computed(() => this.areas().find((a) => a.id === this.activeAreaId()) ?? this.areas()[0] ?? null);
  /** Mesas abiertas (una por servicio, aunque ocupe varias mesas juntas) */
  readonly openSessions = computed(() => {
    const seen = new Map<number, NonNullable<FloorTable['session']>>();
    for (const t of this.areas().flatMap((a) => a.tables)) {
      if (t.session && !seen.has(t.session.id)) seen.set(t.session.id, t.session);
    }
    return [...seen.values()].sort((a, b) => a.openedAt.localeCompare(b.openedAt));
  });

  readonly session = signal<TableSession | null>(null);
  readonly company = signal<Company | null>(null);
  readonly day = signal<CashDay | null>(null);
  dayDate = today();
  readonly now = signal(new Date());

  // Cobro
  readonly paying = signal(false);
  readonly bill = signal<Bill | null>(null);
  readonly mode = signal<PayMode>('total');
  readonly method = signal<PaymentMethod>('EFECTIVO');
  readonly guest = signal<number | null>(null);
  readonly parts = signal(2);
  readonly cashGiven = signal<number | null>(null);
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

  /** Precuenta: mismos productos (con los mismos extras) juntos; "Pizza + huevo" */
  readonly receiptLines = computed<ReceiptLine[]>(() => {
    const lines = new Map<string, ReceiptLine>();
    for (const l of this.session()?.lines ?? []) {
      const extras = l.extras.map((e) => e.name.toLowerCase());
      const key = `${l.productId}|${l.unitPrice}|${extras.join(',')}`;
      const name = [l.productName, ...extras].join(' + ');
      const line = lines.get(key) ?? { name, quantity: 0, unitPrice: l.unitPrice, subtotal: 0 };
      line.quantity += l.quantity;
      line.subtotal += l.subtotal;
      lines.set(key, line);
    }
    return [...lines.values()];
  });

  /** "con huevo, sin cebolla" de un producto de la cuenta. */
  customization(line: ServiceLine): string {
    return customizationText(line.removedIngredients, line.extras.map((e) => e.name));
  }

  ngOnInit(): void {
    this.loadFloor();
    this.loadDay();
    this.companyService.get().subscribe((company) => this.company.set(company));
    const changes = this.live.on('floor').subscribe(() => {
      this.loadFloor();
      this.reloadSession();
      this.loadDay();
    });
    const timer = setInterval(() => {
      this.now.set(new Date());
      this.loadFloor();
    }, REFRESH_MS);
    this.destroyRef.onDestroy(() => {
      changes.unsubscribe();
      clearInterval(timer);
    });
  }

  loadFloor(): void {
    this.service.floor().subscribe({
      next: (areas) => this.areas.set(areas),
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  loadDay(): void {
    this.service.day(this.dayDate).subscribe({
      next: (day) => this.day.set(day),
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  select(sessionId: number): void {
    this.service.get(sessionId).subscribe({
      next: (session) => this.session.set(session),
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  pickTable(table: FloorTable): void {
    if (table.session) this.select(table.session.id);
  }

  private reloadSession(): void {
    const current = this.session();
    if (!current || this.paying()) return;
    this.service.get(current.id).subscribe({
      next: (session) => this.session.set(session.status === 'OPEN' ? session : null),
    });
  }

  minutes(openedAt: string): number {
    return Math.max(0, Math.floor((this.now().getTime() - new Date(openedAt).getTime()) / 60_000));
  }

  /** Segunda linea de una mesa del plano: lo que lleva gastado. */
  info(table: FloorTable): string | null {
    return table.session ? `${table.session.total.toFixed(2).replace('.', ',')} €` : null;
  }

  // ----- Cobro -----

  openPay(): void {
    const session = this.session();
    if (!session) return;
    this.service.bill(session.id).subscribe({
      next: (bill) => {
        this.bill.set(bill);
        this.mode.set('total');
        this.method.set('EFECTIVO');
        this.guest.set(bill.guests.find((g) => !g.paid)?.guest ?? null);
        this.parts.set(Math.max(2, session.guests));
        this.cashGiven.set(null);
        this.closeWhenPaid = true;
        this.paying.set(true);
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  changeParts(delta: number): void {
    this.parts.set(Math.min(20, Math.max(2, this.parts() + delta)));
  }

  setCash(value: number | null): void {
    this.cashGiven.set(value);
  }

  pay(): void {
    const session = this.session();
    if (!session || !this.canPay()) return;
    const mode = this.mode();
    const label = mode === 'total' ? 'Total' : mode === 'parts' ? `Parte de ${this.parts()}` : null;
    this.service
      .pay(session.id, {
        amount: this.amount(),
        method: this.method(),
        cashGiven: this.method() === 'EFECTIVO' ? this.cashGiven() : null,
        guest: mode === 'guest' ? this.guest() : null,
        label,
        closeWhenPaid: this.closeWhenPaid,
      })
      .subscribe({
        next: (updated) => {
          this.paying.set(false);
          const change = this.change();
          const changeText = change ? ` · cambio ${change.toFixed(2).replace('.', ',')} €` : '';
          if (updated.status === 'CLOSED') {
            this.notify.success(`Mesa ${updated.tableLabel} cobrada y libre${changeText}`);
            this.session.set(null);
          } else {
            this.notify.success(`Cobrados ${this.amountText(updated.paid)} de ${this.amountText(updated.total)}${changeText}`);
            this.session.set(updated);
          }
        },
        error: (err) => this.notify.error(apiErrorMessage(err)),
      });
  }

  async removePayment(payment: Payment): Promise<void> {
    const session = this.session();
    if (!session) return;
    const accepted = await this.confirmService.ask({
      title: 'Anular cobro',
      message: `¿Anular el cobro de ${this.amountText(payment.amount)} (${payment.label})?`,
      confirmText: 'Anular',
    });
    if (!accepted) return;
    this.service.removePayment(session.id, payment.id).subscribe({
      next: (updated) => this.session.set(updated),
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  release(): void {
    const session = this.session();
    if (!session) return;
    this.service.close(session.id).subscribe({
      next: () => {
        this.notify.success(`Mesa ${session.tableLabel} libre`);
        this.session.set(null);
      },
      error: (err) => this.notify.error(apiErrorMessage(err)),
    });
  }

  /** Imprime la precuenta (solo se imprime el bloque .receipt, a 80 mm). */
  printReceipt(): void {
    window.print();
  }

  private amountText(value: number): string {
    return `${value.toFixed(2).replace('.', ',')} €`;
  }
}

function today(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
