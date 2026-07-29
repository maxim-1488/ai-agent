import type { Task } from './task';

export type TaskEventType =
  | 'TASK_CREATED'
  | 'TASK_STARTED'
  | 'TASK_PROGRESS'
  | 'TASK_COMPLETED'
  | 'TASK_FAILED'
  | 'TASK_CANCELLED'
  | 'SUBSCRIBED'
  | 'UNSUBSCRIBED'
  | 'ERROR'
  | 'PONG';

export interface TaskEvent {
  type: TaskEventType;
  taskId?: string;
  task?: Task;
  message?: string;
}

export type ConnectionStatus = 'connecting' | 'connected' | 'reconnecting' | 'disconnected' | 'lost';
