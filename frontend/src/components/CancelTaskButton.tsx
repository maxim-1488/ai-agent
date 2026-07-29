import type { Task } from '../types';

export function CancelTaskButton({ task, onCancel }: { task: Task; onCancel(): void }) {
  const disabled = ['COMPLETED', 'FAILED', 'CANCELLED', 'TIMED_OUT'].includes(task.status);
  return <button disabled={disabled} onClick={onCancel}>Cancel</button>;
}
