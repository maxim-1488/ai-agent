import { useCallback, useState } from 'react';
import { cancelTask, createTask, getTask, PROMPT_MAX_LENGTH } from '../api/taskApi';
import type { UserFacingError } from '../model/apiError';
import type { Task } from '../model/task';

export function useTask(clientId: string, onTaskChanged: (task: Task) => void) {
  const [selectedTask, setSelectedTask] = useState<Task | null>(null);
  const [creating, setCreating] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [error, setError] = useState<UserFacingError | null>(null);

  const applyTask = useCallback((task: Task | null) => {
    setSelectedTask(task);
    if (task) onTaskChanged(task);
  }, [onTaskChanged]);

  const selectTask = useCallback(async (taskId: string) => {
    setError(null);
    const task = await getTask(clientId, taskId);
    applyTask(task);
    return task;
  }, [applyTask, clientId]);

  const refreshSelectedTask = useCallback(async () => {
    if (!selectedTask) return null;
    try {
      const task = await getTask(clientId, selectedTask.id);
      applyTask(task);
      return task;
    } catch (e) {
      setError(e as UserFacingError);
      return null;
    }
  }, [applyTask, clientId, selectedTask]);

  const submitPrompt = useCallback(async (prompt: string) => {
    const normalized = prompt.trim();
    setError(null);
    if (!normalized) {
      setError({ message: 'Введите задание для AI.', fieldErrors: { prompt: 'Prompt не должен быть пустым.' } });
      return null;
    }
    if (normalized.length > PROMPT_MAX_LENGTH) {
      setError({ message: `Задание слишком длинное. Максимум ${PROMPT_MAX_LENGTH} символов.`, fieldErrors: { prompt: `Максимум ${PROMPT_MAX_LENGTH} символов.` } });
      return null;
    }
    setCreating(true);
    try {
      const task = await createTask(clientId, normalized);
      applyTask(task);
      return task;
    } catch (e) {
      setError(e as UserFacingError);
      return null;
    } finally {
      setCreating(false);
    }
  }, [applyTask, clientId]);

  const stopTask = useCallback(async () => {
    if (!selectedTask) return null;
    setCancelling(true);
    setError(null);
    try {
      const task = await cancelTask(clientId, selectedTask.id);
      applyTask(task);
      return task;
    } catch (e) {
      setError(e as UserFacingError);
      return null;
    } finally {
      setCancelling(false);
    }
  }, [applyTask, clientId, selectedTask]);

  return { selectedTask, setSelectedTask: applyTask, creating, cancelling, error, setError, selectTask, refreshSelectedTask, submitPrompt, stopTask };
}
