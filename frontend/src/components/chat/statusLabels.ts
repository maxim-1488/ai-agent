import type { TaskStatus } from '../../model/task';

export const statusLabels: Record<TaskStatus, string> = {
  CREATED: 'ожидает запуска',
  IN_PROGRESS: 'выполняется',
  COMPLETED: 'завершена',
  FAILED: 'ошибка',
  CANCELLED: 'остановлена',
  TIMED_OUT: 'превышено время'
};
