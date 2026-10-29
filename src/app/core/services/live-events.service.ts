import { Injectable, inject, signal } from '@angular/core';
import { Observable, Subject, filter, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

/** Productos que cocina o barra acaba de dejar listos para una mesa. */
export interface ReadyEvent {
  sessionId: number;
  tableLabel: string;
  waiterName: string | null;
  items: string[];
}

/** Pedido a domicilio que acaba de quedar listo: aviso a los repartidores. */
export interface DeliveryEvent {
  orderId: number;
  customerName: string;
  address: string;
  time: string;
}

/** Espera antes de reconectar: crece hasta 15 s si el servidor no responde. */
const RETRY_MS = [1_000, 2_000, 5_000, 10_000, 15_000];

/**
 * Avisos en tiempo real del servidor (Server-Sent Events en /api/events).
 * Se lee con fetch en lugar de EventSource para mandar el token en la cabecera Authorization
 * (EventSource no admite cabeceras y el token no debe ir en la URL). Si se corta, vuelve a conectar.
 */
@Injectable({ providedIn: 'root' })
export class LiveEventsService {
  private readonly auth = inject(AuthService);

  /** true mientras la conexion esta abierta */
  readonly connected = signal(false);

  private readonly changes = new Subject<string[]>();
  private readonly readySubject = new Subject<ReadyEvent>();
  /** Productos listos para llevar a una mesa */
  readonly ready$: Observable<ReadyEvent> = this.readySubject.asObservable();
  private readonly deliverySubject = new Subject<DeliveryEvent>();
  /** Pedidos a domicilio listos para llevar */
  readonly delivery$: Observable<DeliveryEvent> = this.deliverySubject.asObservable();

  private controller: AbortController | null = null;
  private retry = 0;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;

  /** Avisa cuando cambia algo de ese ambito: "kitchen", "floor", "orders", "delivery" o "session:{id}". */
  on(scope: string): Observable<void> {
    return this.changes.pipe(
      filter((scopes) => scopes.includes(scope)),
      map(() => undefined),
    );
  }

  start(): void {
    if (this.controller) return;
    this.controller = new AbortController();
    this.connect(this.controller);
  }

  stop(): void {
    this.controller?.abort();
    this.controller = null;
    if (this.retryTimer) clearTimeout(this.retryTimer);
    this.retryTimer = null;
    this.connected.set(false);
  }

  private async connect(controller: AbortController): Promise<void> {
    const token = this.auth.token();
    if (!token) {
      this.stop();
      return;
    }
    try {
      const response = await fetch(`${environment.apiBaseUrl}/events`, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
        signal: controller.signal,
      });
      if (response.status === 401 || response.status === 403) {
        this.stop();
        return;
      }
      if (!response.ok || !response.body) throw new Error(`HTTP ${response.status}`);
      this.connected.set(true);
      this.retry = 0;
      await this.read(response.body);
    } catch {
      // Cortado o sin servidor: se reintenta abajo
    }
    this.connected.set(false);
    if (controller.signal.aborted || this.controller !== controller) return;
    const wait = RETRY_MS[Math.min(this.retry++, RETRY_MS.length - 1)];
    this.retryTimer = setTimeout(() => {
      if (this.controller === controller) this.connect(controller);
    }, wait);
  }

  /** Lee el flujo: los mensajes van separados por una linea en blanco ("event: x" + "data: {...}"). */
  private async read(body: ReadableStream<Uint8Array>): Promise<void> {
    const reader = body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    for (;;) {
      const { value, done } = await reader.read();
      if (done) return;
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n');
      let end: number;
      while ((end = buffer.indexOf('\n\n')) >= 0) {
        this.dispatch(buffer.slice(0, end));
        buffer = buffer.slice(end + 2);
      }
    }
  }

  private dispatch(block: string): void {
    let name = 'message';
    const data: string[] = [];
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) name = line.slice(6).trim();
      else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
      // las lineas que empiezan por ":" son el latido: no llevan nada
    }
    if (!data.length) return;
    try {
      if (name === 'change') this.changes.next((JSON.parse(data.join('\n')) as { scopes: string[] }).scopes);
      else if (name === 'ready') this.readySubject.next(JSON.parse(data.join('\n')) as ReadyEvent);
      else if (name === 'delivery') this.deliverySubject.next(JSON.parse(data.join('\n')) as DeliveryEvent);
    } catch {
      // mensaje mal formado: se ignora
    }
  }
}
