import type { Task } from '../../model/task';
import { statusLabels } from './statusLabels';

function activityLabel(status: Task['status']): string {
  return status === 'CREATED' ? 'Жду запуск…' : 'Обрабатываю задачу…';
}

export function TaskProgress({ task, onCancel, cancelling }: { task: Task; onCancel: () => void; cancelling: boolean }) {
  return (
    <div className="task-progress">
      <div className="task-progress__header">
        <span>{activityLabel(task.status)}</span>
        <span className="task-progress__dots" aria-hidden="true">
          <span />
          <span />
          <span />
        </span>
      </div>
      <div className="task-progress__footer">
        <span>Статус: {statusLabels[task.status]}</span>
        <button type="button" className="button button--ghost" onClick={onCancel} disabled={cancelling}>
          {cancelling ? 'Останавливаю…' : 'Остановить'}
        </button>
      </div>
    </div>
  );
}
