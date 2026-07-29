export type TaskStatus = 'CREATED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'TIMED_OUT';

export interface Task {
  id: string;
  prompt?: string | null;
  status: TaskStatus;
  progress: number;
  result?: string | null;
  errorMessage?: string | null;
  createdAt: string;
  startedAt?: string | null;
  completedAt?: string | null;
  updatedAt: string;
  version?: number;
  taskUrl?: string;
  webSocketUrl?: string;
}

export interface TaskListResponse {
  items: Task[];
  total: number;
  page: number;
  size: number;
}

export const TERMINAL_STATUSES: TaskStatus[] = ['COMPLETED', 'FAILED', 'CANCELLED', 'TIMED_OUT'];

export function isTerminalStatus(status: TaskStatus): boolean {
  return TERMINAL_STATUSES.includes(status);
}

export function canCancel(status: TaskStatus): boolean {
  return status === 'CREATED' || status === 'IN_PROGRESS';
}
