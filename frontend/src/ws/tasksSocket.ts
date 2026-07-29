import type { Task } from '../types';

export type SocketStatus = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'ERROR';

export interface TaskSocketCallbacks {
  onStatus(status: SocketStatus): void;
  onTask(task: Task): void;
  onError(message: string): void;
}

export class TasksSocket {
  private ws?: WebSocket;

  constructor(private readonly clientId: string, private readonly callbacks: TaskSocketCallbacks) {}

  connect() {
    this.callbacks.onStatus('CONNECTING');
    const configuredUrl = String(import.meta.env.VITE_WS_URL ?? '').trim();
    const base = configuredUrl || `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/tasks`;
    const separator = base.includes('?') ? '&' : '?';
    this.ws = new WebSocket(`${base}${separator}clientId=${encodeURIComponent(this.clientId)}`, []);
    this.ws.onopen = () => this.callbacks.onStatus('CONNECTED');
    this.ws.onclose = () => this.callbacks.onStatus('DISCONNECTED');
    this.ws.onerror = () => this.callbacks.onStatus('ERROR');
    this.ws.onmessage = event => {
      const message = JSON.parse(event.data);
      if (message.type === 'ERROR') this.callbacks.onError(message.message);
      if (message.task) this.callbacks.onTask(message.task);
    };
  }

  subscribe(taskId: string) {
    this.ws?.send(JSON.stringify({ action: 'SUBSCRIBE', taskId, clientId: this.clientId }));
  }

  unsubscribe(taskId: string) {
    this.ws?.send(JSON.stringify({ action: 'UNSUBSCRIBE', taskId }));
  }

  close() {
    this.ws?.close();
  }
}
