import { sanitizeBackendMessage } from '../../model/apiError';

export function TaskError({ message }: { message?: string | null }) {
  return (
    <div className="task-error">
      <p>Не удалось выполнить задание.</p>
      {message && <small>{sanitizeBackendMessage(message, '')}</small>}
    </div>
  );
}
