import type { ConnectionStatus, TaskEvent } from '../model/taskEvent';

interface TaskWebSocketOptions {
  clientId: string;
  onStatus(status: ConnectionStatus): void;
  onEvent(event: TaskEvent): void;
  onReconnect(): void;
}

export class TaskWebSocket {
  private ws: WebSocket | null = null;
  private activeTaskId: string | null = null;
  private closedByUser = false;
  private reconnectTimer: number | null = null;
  private reconnectAttempt = 0;

  constructor(private readonly options: TaskWebSocketOptions) {}

  connect(): void {
    this.closedByUser = false;
    this.options.onStatus(this.reconnectAttempt > 0 ? 'reconnecting' : 'connecting');
    const configuredUrl = String(import.meta.env.VITE_WS_URL ?? '').trim();
    const base = configuredUrl || `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/tasks`;
    const separator = base.includes('?') ? '&' : '?';
    this.ws = new WebSocket(`${base}${separator}clientId=${encodeURIComponent(this.options.clientId)}`);

    this.ws.onopen = () => {
      const wasReconnect = this.reconnectAttempt > 0;
      this.reconnectAttempt = 0;
      this.options.onStatus('connected');
      if (this.activeTaskId) this.send({ action: 'SUBSCRIBE', taskId: this.activeTaskId, clientId: this.options.clientId });
      if (wasReconnect) this.options.onReconnect();
    };

    this.ws.onmessage = event => {
      try {
        this.options.onEvent(JSON.parse(event.data) as TaskEvent);
      } catch {
        this.options.onEvent({ type: 'ERROR', message: 'Некорректное WebSocket-сообщение.' });
      }
    };

    this.ws.onerror = () => {
      this.options.onStatus('lost');
    };

    this.ws.onclose = () => {
      this.ws = null;
      if (this.closedByUser) {
        this.options.onStatus('disconnected');
        return;
      }
      this.scheduleReconnect();
    };
  }

  subscribe(taskId: string): void {
    if (this.activeTaskId && this.activeTaskId !== taskId) this.unsubscribe(this.activeTaskId);
    this.activeTaskId = taskId;
    this.send({ action: 'SUBSCRIBE', taskId, clientId: this.options.clientId });
  }

  unsubscribe(taskId: string): void {
    if (this.activeTaskId === taskId) this.activeTaskId = null;
    this.send({ action: 'UNSUBSCRIBE', taskId, clientId: this.options.clientId });
  }

  ping(): void {
    this.send({ action: 'PING', clientId: this.options.clientId });
  }

  close(): void {
    this.closedByUser = true;
    if (this.reconnectTimer) window.clearTimeout(this.reconnectTimer);
    if (this.activeTaskId) this.send({ action: 'UNSUBSCRIBE', taskId: this.activeTaskId, clientId: this.options.clientId });
    this.ws?.close();
    this.ws = null;
    this.options.onStatus('disconnected');
  }

  private send(payload: Record<string, unknown>): void {
    if (this.ws?.readyState === WebSocket.OPEN) this.ws.send(JSON.stringify(payload));
  }

  private scheduleReconnect(): void {
    this.reconnectAttempt += 1;
    this.options.onStatus(this.reconnectAttempt === 1 ? 'lost' : 'reconnecting');
    const delay = Math.min(1000 * 2 ** (this.reconnectAttempt - 1), 8000);
    this.reconnectTimer = window.setTimeout(() => this.connect(), delay);
  }
}
