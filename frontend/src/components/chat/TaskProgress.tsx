import type { Task } from '../../model/task';
import { statusLabels } from './statusLabels';

export function TaskProgress({ task, onCancel, cancelling }: { task: Task; onCancel: () => void; cancelling: boolean }) {
  const progress = Math.max(0, Math.min(100, task.progress ?? 0));
  return (
    <div className="task-progress">
      <div className="task-progress__header">
        <span>{task.status === 'CREATED' ? 'Агент ожидает запуска' : 'Агент выполняет задачу'}</span>
        <strong>{progress}%</strong>
      </div>
      <div className="task-progress__bar" aria-label="Task progress">
        <div style={{ width: `${progress}%` }} />
      </div>
      <div className="task-progress__footer">
        <span>{statusLabels[task.status]}</span>
        <button type="button" className="button button--ghost" onClick={onCancel} disabled={cancelling}>
          {cancelling ? 'Останавливаю…' : 'Остановить'}
        </button>
      </div>
    </div>
  );
}
