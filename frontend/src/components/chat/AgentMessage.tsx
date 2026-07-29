import type { Task } from '../../model/task';
import type { ReactNode } from 'react';
import { canCancel } from '../../model/task';
import { TaskError } from './TaskError';
import { TaskProgress } from './TaskProgress';

export function AgentMessage({ task, onCancel, cancelling }: { task: Task; onCancel: () => void; cancelling: boolean }) {
  let content: ReactNode;
  if (canCancel(task.status)) {
    content = <TaskProgress task={task} onCancel={onCancel} cancelling={cancelling} />;
  } else if (task.status === 'COMPLETED') {
    content = <div className="message__text">{task.result || 'Задача завершена без текстового результата.'}</div>;
  } else if (task.status === 'CANCELLED') {
    content = <div className="message__muted">Выполнение задачи остановлено</div>;
  } else {
    content = <TaskError message={task.errorMessage} />;
  }

  return (
    <article className="message message--agent">
      <div className="message__author">ChatAI</div>
      <div className="message__content">{content}</div>
    </article>
  );
}
