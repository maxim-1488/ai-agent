import type { ConnectionStatus as Status } from '../../model/taskEvent';

const LABELS: Record<Status, string> = {
  connecting: 'подключение',
  connected: 'подключено',
  reconnecting: 'переподключение',
  disconnected: 'отключено',
  lost: 'соединение потеряно'
};

export function ConnectionStatus({ status }: { status: Status }) {
  return (
    <div className={`connection-status connection-status--${status}`} aria-label={`WebSocket: ${LABELS[status]}`}>
      <span />
      {LABELS[status]}
    </div>
  );
}
