import type { SocketStatus } from '../ws/tasksSocket';

export function WebSocketConnectionStatus({ status }: { status: SocketStatus }) {
  return <div aria-label="WebSocket status">WebSocket: {status}</div>;
}
