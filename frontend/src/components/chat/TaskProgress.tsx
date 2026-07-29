import { useEffect, useMemo, useState } from 'react';
import type { Task } from '../../model/task';
import { statusLabels } from './statusLabels';

export function TaskProgress({ task, onCancel, cancelling }: { task: Task; onCancel: () => void; cancelling: boolean }) {
  const steps = useMemo(() => task.status === 'CREATED'
    ? ['Готовлю контекст', 'Жду запуск', 'Настраиваю агента']
    : ['Ищу детали', 'Собираю информацию', 'Считаю байты', 'Сверяю факты', 'Формирую ответ'], [task.status]);
  const initialStepIndex = Math.abs(task.id.split('').reduce((sum, char) => sum + char.charCodeAt(0), task.progress ?? 0)) % steps.length;
  const [stepIndex, setStepIndex] = useState(initialStepIndex);
  const activityLabel = `${steps[stepIndex]}…`;

  useEffect(() => {
    setStepIndex(initialStepIndex);
    const timerId = window.setInterval(() => {
      setStepIndex(current => (current + 1) % steps.length);
    }, 1900);

    return () => window.clearInterval(timerId);
  }, [initialStepIndex, steps]);

  return (
    <div className="task-progress">
      <div className="task-progress__header">
        <span>{activityLabel}</span>
        <span className="task-progress__dots" aria-hidden="true">
          <span />
          <span />
          <span />
        </span>
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
