import type { Task, TaskListResponse } from '../model/task';
import { apiRequest } from './apiClient';

export const PROMPT_MAX_LENGTH = Number(import.meta.env.VITE_PROMPT_MAX_LENGTH ?? 4000);

export function createTask(clientId: string, prompt: string): Promise<Task> {
  return apiRequest<Task>('/api/v1/tasks', clientId, {
    method: 'POST',
    body: JSON.stringify({ prompt })
  });
}

export function getTask(clientId: string, taskId: string): Promise<Task> {
  return apiRequest<Task>(`/api/v1/tasks/${taskId}`, clientId);
}

export function listTasks(clientId: string): Promise<TaskListResponse> {
  return apiRequest<TaskListResponse>('/api/v1/tasks?size=50&sort=updatedAt&direction=DESC', clientId);
}

export function cancelTask(clientId: string, taskId: string): Promise<Task> {
  return apiRequest<Task>(`/api/v1/tasks/${taskId}/cancel`, clientId, { method: 'POST' });
}
