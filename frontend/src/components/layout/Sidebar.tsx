import type { Task } from '../../model/task';
import { statusLabels } from '../chat/statusLabels';
import { LoadingIndicator } from '../common/LoadingIndicator';

function titleFor(task: Task): string {
  return task.prompt?.trim() || `Задача ${task.id.slice(0, 8)}`;
}

export function Sidebar({
  tasks,
  activeTaskId,
  loading,
  open,
  onClose,
  onNewTask,
  onSelectTask
}: {
  tasks: Task[];
  activeTaskId: string | null;
  loading: boolean;
  open: boolean;
  onClose(): void;
  onNewTask(): void;
  onSelectTask(id: string): void;
}) {
  return (
    <>
      <aside className={`sidebar ${open ? 'sidebar--open' : ''}`}>
        <div className="sidebar__header">
          <div className="sidebar__brand">ChatAI</div>
          <button type="button" className="sidebar__close" onClick={onClose} aria-label="Закрыть sidebar">×</button>
        </div>
        <button type="button" className="new-task" onClick={onNewTask}>
          <span className="new-task__icon" aria-hidden="true">
            <svg viewBox="0 0 20 20" focusable="false">
              <path d="M10 4.25v11.5M4.25 10h11.5" />
            </svg>
          </span>
          <span>Новый чат</span>
        </button>
        <div className="sidebar__list" aria-label="Список задач">
          {loading && <LoadingIndicator label="Загрузка задач" />}
          {!loading && tasks.length === 0 && <div className="sidebar__empty">Задач пока нет</div>}
          {tasks.map(task => (
            <button
              type="button"
              key={task.id}
              className={`task-item ${task.id === activeTaskId ? 'task-item--active' : ''}`}
              onClick={() => onSelectTask(task.id)}
            >
              <span className="task-item__title">{titleFor(task)}</span>
              <span className={`task-item__status task-item__status--${task.status.toLowerCase()}`}>{statusLabels[task.status]}</span>
            </button>
          ))}
        </div>
        <div className="sidebar__copyright">© 2026 Powered by MrKrylov</div>
      </aside>
      {open && <button type="button" className="sidebar-backdrop" aria-label="Закрыть sidebar" onClick={onClose} />}
    </>
  );
}
