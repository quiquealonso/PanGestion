import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Company } from '../../core/models/company.model';
import {
  CashDay,
  FloorArea,
  FloorTable,
  ITEM_STATUS_LABELS,
  Payment,
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
import { PayDialogComponent } from '../../shared/pay-dialog.component';

/** Respaldo por si se corta la conexion de avisos en tiempo real. */
const REFRESH_MS = 60_000;

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
  imports: [CurrencyPipe, DatePipe, FormsModule, DiningTableComponent, PayDialogComponent],
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

  /** Dialogo de cobro abierto (`app-pay-dialog`, compartido con la comanda de la mesa) */
  readonly paying = signal(false);

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
    if (this.session()) this.paying.set(true);
  }

  /** Cobro hecho: si la mesa ha quedado libre, se deja de mostrar. */
  onPaid(updated: TableSession): void {
    this.paying.set(false);
    this.session.set(updated.status === 'CLOSED' ? null : updated);
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
