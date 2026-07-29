import type { Task } from '../types';
import { CancelTaskButton } from './CancelTaskButton';
import { TaskErrorView } from './TaskErrorView';
import { TaskProgressBar } from './TaskProgressBar';
import { TaskResultView } from './TaskResultView';

export function TaskStatusView({ task, onCancel }: { task: Task; onCancel(): void }) {
  return (
    <article>
      <h2>Task {task.id}</h2>
      <div>Status: {task.status}</div>
      <TaskProgressBar progress={task.progress} />
      <TaskResultView result={task.result} />
      <TaskErrorView error={task.errorMessage} />
      <CancelTaskButton task={task} onCancel={onCancel} />
    </article>
  );
}
