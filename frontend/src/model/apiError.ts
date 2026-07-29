export interface BackendError {
  errorCode?: string;
  message?: string;
  correlationId?: string;
  details?: Array<{ field?: string; message?: string }>;
  timestamp?: string;
}

export interface UserFacingError {
  message: string;
  fieldErrors: Record<string, string>;
  status?: number;
  code?: string;
}

const INTERNAL_PATTERNS = [/exception/i, /stack trace/i, /\bat\s+[\w.$]+\(.*:\d+\)/i, /select\s+|insert\s+|update\s+|delete\s+/i, /org\.|java\.|io\.vertx/i];

export function sanitizeBackendMessage(message: unknown, fallback = 'Не удалось выполнить запрос.'): string {
  if (typeof message !== 'string' || !message.trim()) return fallback;
  const normalized = message.trim();
  if (INTERNAL_PATTERNS.some(pattern => pattern.test(normalized))) return fallback;
  return normalized.length > 240 ? `${normalized.slice(0, 240)}…` : normalized;
}

export function mapApiError(error: unknown): UserFacingError {
  if (error instanceof DOMException && error.name === 'AbortError') {
    return { message: 'Backend не ответил вовремя. Повторите попытку.', fieldErrors: {} };
  }
  if (error instanceof TypeError) {
    return { message: 'Нет соединения с backend. Проверьте, что сервер запущен.', fieldErrors: {} };
  }
  if (typeof error === 'object' && error !== null) {
    const value = error as BackendError & { status?: number };
    const fieldErrors = Object.fromEntries((value.details ?? []).map(item => [item.field ?? 'request', sanitizeBackendMessage(item.message, 'Некорректное значение.')]));
    if (value.status === 404) return { message: 'Задача не найдена или недоступна.', fieldErrors, status: value.status, code: value.errorCode };
    if (value.status === 409) return { message: 'Задача уже находится в состоянии, несовместимом с действием.', fieldErrors, status: value.status, code: value.errorCode };
    if (value.status && value.status >= 500) return { message: 'Backend временно не смог обработать запрос.', fieldErrors, status: value.status, code: value.errorCode };
    return { message: sanitizeBackendMessage(value.message), fieldErrors, status: value.status, code: value.errorCode };
  }
  return { message: 'Не удалось выполнить запрос.', fieldErrors: {} };
}
