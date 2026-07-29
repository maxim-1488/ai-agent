import { useCallback, useEffect, useState } from 'react';
import { listTasks } from '../api/taskApi';
import type { UserFacingError } from '../model/apiError';
import type { Task } from '../model/task';

export function useTasks(clientId: string) {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<UserFacingError | null>(null);

  const refreshTasks = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await listTasks(clientId);
      setTasks(page.items);
    } catch (e) {
      setError(e as UserFacingError);
    } finally {
      setLoading(false);
    }
  }, [clientId]);

  const upsertTask = useCallback((task: Task) => {
    setTasks(current => {
      const without = current.filter(item => item.id !== task.id);
      return [task, ...without].sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt));
    });
  }, []);

  useEffect(() => {
    void refreshTasks();
  }, [refreshTasks]);

  return { tasks, loading, error, refreshTasks, upsertTask };
}
