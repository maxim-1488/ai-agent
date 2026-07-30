import '@testing-library/jest-dom/vitest';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import type { Task } from './model/task';

const baseTask: Task = {
  id: 'task-1',
  prompt: 'Проанализируй текст',
  status: 'CREATED',
  progress: 0,
  createdAt: '2026-07-28T10:00:00Z',
  updatedAt: '2026-07-28T10:00:00Z',
  taskUrl: '/api/v1/tasks/task-1',
  webSocketUrl: '/ws/tasks'
};

class MockWebSocket {
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSED = 3;
  static instances: MockWebSocket[] = [];
  readyState = MockWebSocket.CONNECTING;
  sent: string[] = [];
  onopen?: () => void;
  onclose?: () => void;
  onerror?: () => void;
  onmessage?: (event: MessageEvent) => void;

  constructor(public url: string) {
    MockWebSocket.instances.push(this);
  }

  open() {
    this.readyState = MockWebSocket.OPEN;
    this.onopen?.();
  }

  emit(data: unknown) {
    this.onmessage?.({ data: JSON.stringify(data) } as MessageEvent);
  }

  send(data: string) {
    this.sent.push(data);
  }

  close() {
    this.readyState = MockWebSocket.CLOSED;
    this.onclose?.();
  }

  breakConnection() {
    this.readyState = MockWebSocket.CLOSED;
    this.onclose?.();
  }
}

function jsonResponse(body: unknown, ok = true, status = ok ? 200 : 400) {
  return { ok, status, statusText: ok ? 'OK' : 'Bad request', json: async () => body };
}

function mockFetch(impl: (input: RequestInfo | URL, init?: RequestInit) => unknown) {
  vi.stubGlobal('fetch', vi.fn(impl));
}

beforeEach(() => {
  localStorage.clear();
  MockWebSocket.instances = [];
  vi.stubGlobal('WebSocket', MockWebSocket);
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('AI Agent frontend', () => {
  it('отображает empty state', async () => {
    mockFetch(() => jsonResponse({ items: [], total: 0, page: 0, size: 50 }));

    render(<App />);

    expect(await screen.findByText('Какую задачу выполнить?')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Введите задание для AI...')).toBeInTheDocument();
  });

  it('создаёт задачу и отображает prompt пользователя', async () => {
    mockFetch((input, init) => {
      if (String(input).includes('/api/v1/tasks') && init?.method === 'POST') return jsonResponse(baseTask, true, 201);
      return jsonResponse({ items: [], total: 0, page: 0, size: 50 });
    });

    render(<App />);
    MockWebSocket.instances[0].open();
    await userEvent.type(screen.getByLabelText('Введите задание для AI'), baseTask.prompt!);
    await userEvent.click(screen.getByLabelText('Отправить задание'));

    await waitFor(() => expect(screen.getAllByText(baseTask.prompt!).length).toBeGreaterThanOrEqual(1));
    expect(screen.getByText('Жду запуск…')).toBeInTheDocument();
  });

  it('продолжает текущую цепочку задач до нажатия новой задачи', async () => {
    const prompts: string[] = [];
    mockFetch((input, init) => {
      if (String(input).includes('/api/v1/tasks') && init?.method === 'POST') {
        const body = JSON.parse(String(init.body)) as { prompt: string };
        prompts.push(body.prompt);
        return jsonResponse({
          ...baseTask,
          id: `task-${prompts.length}`,
          prompt: body.prompt,
          updatedAt: `2026-07-28T10:00:0${prompts.length}Z`
        }, true, 201);
      }
      return jsonResponse({ items: [], total: 0, page: 0, size: 50 });
    });

    const { container } = render(<App />);
    MockWebSocket.instances[0].open();

    await userEvent.type(screen.getByLabelText('Введите задание для AI'), 'first prompt');
    await userEvent.click(screen.getByLabelText('Отправить задание'));
    await userEvent.type(screen.getByLabelText('Введите задание для AI'), 'second prompt');
    await userEvent.click(screen.getByLabelText('Отправить задание'));

    await waitFor(() => expect(screen.getAllByText('first prompt').length).toBeGreaterThanOrEqual(2));
    expect(await screen.findByText('second prompt')).toBeInTheDocument();
    expect(prompts).toEqual(['first prompt', 'second prompt']);
    expect(container.querySelectorAll('.task-item')).toHaveLength(1);
  });

  it('отображает progress и обрабатывает TASK_PROGRESS', async () => {
    mockFetch((input, init) => {
      if (String(input).includes('/api/v1/tasks') && init?.method === 'POST') return jsonResponse(baseTask, true, 201);
      return jsonResponse({ items: [], total: 0, page: 0, size: 50 });
    });

    render(<App />);
    const socket = MockWebSocket.instances[0];
    socket.open();
    await userEvent.type(screen.getByLabelText('Введите задание для AI'), baseTask.prompt!);
    await userEvent.click(screen.getByLabelText('Отправить задание'));

    act(() => socket.emit({ type: 'TASK_PROGRESS', task: { ...baseTask, status: 'IN_PROGRESS', progress: 42 } }));

    expect(await screen.findByText('Обрабатываю задачу…')).toBeInTheDocument();
    expect(screen.queryByText('42%')).not.toBeInTheDocument();
  });

  it('показывает result после TASK_COMPLETED', async () => {
    mockFetch((input, init) => {
      if (String(input).includes('/api/v1/tasks') && init?.method === 'POST') return jsonResponse(baseTask, true, 201);
      return jsonResponse({ items: [], total: 0, page: 0, size: 50 });
    });

    render(<App />);
    const socket = MockWebSocket.instances[0];
    socket.open();
    await userEvent.type(screen.getByLabelText('Введите задание для AI'), baseTask.prompt!);
    await userEvent.click(screen.getByLabelText('Отправить задание'));

    act(() => socket.emit({ type: 'TASK_COMPLETED', task: { ...baseTask, status: 'COMPLETED', progress: 100, result: 'Готовый результат\nВторая строка' } }));

    expect(await screen.findByText(/Готовый результат/)).toBeInTheDocument();
    expect(screen.queryByText('Остановить')).not.toBeInTheDocument();
  });

  it('показывает безопасную ошибку после TASK_FAILED', async () => {
    mockFetch((input, init) => {
      if (String(input).includes('/api/v1/tasks') && init?.method === 'POST') return jsonResponse(baseTask, true, 201);
      return jsonResponse({ items: [], total: 0, page: 0, size: 50 });
    });

    render(<App />);
    const socket = MockWebSocket.instances[0];
    socket.open();
    await userEvent.type(screen.getByLabelText('Введите задание для AI'), baseTask.prompt!);
    await userEvent.click(screen.getByLabelText('Отправить задание'));

    act(() => socket.emit({ type: 'TASK_FAILED', task: { ...baseTask, status: 'FAILED', errorMessage: 'java.lang.Exception: stack trace' } }));

    expect(await screen.findByText('Не удалось выполнить задание.')).toBeInTheDocument();
    expect(screen.queryByText(/java.lang/)).not.toBeInTheDocument();
  });

  it('отменяет задачу', async () => {
    const inProgress = { ...baseTask, status: 'IN_PROGRESS' as const, progress: 25 };
    const cancelled = { ...baseTask, status: 'CANCELLED' as const, progress: 25 };
    mockFetch((input, init) => {
      if (String(input).endsWith('/cancel')) return jsonResponse(cancelled);
      if (String(input).includes('/api/v1/tasks') && init?.method === 'POST') return jsonResponse(inProgress, true, 201);
      return jsonResponse({ items: [], total: 0, page: 0, size: 50 });
    });

    render(<App />);
    MockWebSocket.instances[0].open();
    await userEvent.type(screen.getByLabelText('Введите задание для AI'), baseTask.prompt!);
    await userEvent.click(screen.getByLabelText('Отправить задание'));
    await userEvent.click(await screen.findByText('Остановить'));

    expect(await screen.findByText('Выполнение задачи остановлено')).toBeInTheDocument();
  });

  it('не показывает кнопку отмены для terminal state', async () => {
    const completed = { ...baseTask, status: 'COMPLETED' as const, progress: 100, result: 'Done' };
    mockFetch(input => {
      if (String(input).includes('/api/v1/tasks/task-1')) return jsonResponse(completed);
      return jsonResponse({ items: [completed], total: 1, page: 0, size: 50 });
    });

    render(<App />);
    await userEvent.click(await screen.findByText(baseTask.prompt!));

    expect(await screen.findByText('Done')).toBeInTheDocument();
    expect(screen.queryByText('Остановить')).not.toBeInTheDocument();
  });

  it('восстанавливает состояние после WebSocket reconnect через REST', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      if (String(input).includes('/api/v1/tasks/task-1')) return Promise.resolve(jsonResponse({ ...baseTask, status: 'IN_PROGRESS', progress: 77 }));
      return Promise.resolve(jsonResponse({ items: [baseTask], total: 1, page: 0, size: 50 }));
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<App />);
    MockWebSocket.instances[0].open();
    await userEvent.click(await screen.findByText(baseTask.prompt!));
    vi.useFakeTimers();
    act(() => MockWebSocket.instances[0].breakConnection());
    await act(async () => {
      vi.advanceTimersByTime(1000);
    });
    act(() => MockWebSocket.instances[1].open());
    vi.useRealTimers();

    expect(await screen.findByText('Обрабатываю задачу…')).toBeInTheDocument();
    expect(screen.queryByText('77%')).not.toBeInTheDocument();
  });

  it('отображает backend error', async () => {
    mockFetch((input, init) => {
      if (String(input).includes('/api/v1/tasks') && init?.method === 'POST') {
        return jsonResponse({ errorCode: 'VALIDATION_ERROR', message: 'Некорректный prompt', details: [{ field: 'prompt', message: 'too short' }] }, false, 400);
      }
      return jsonResponse({ items: [], total: 0, page: 0, size: 50 });
    });

    render(<App />);
    await userEvent.type(screen.getByLabelText('Введите задание для AI'), 'x');
    await userEvent.click(screen.getByLabelText('Отправить задание'));

    expect(await screen.findByRole('alert')).toHaveTextContent('Некорректный prompt');
  });

  it('выбирает задачу в sidebar', async () => {
    const selected = { ...baseTask, status: 'COMPLETED' as const, progress: 100, result: 'Ответ выбранной задачи' };
    mockFetch(input => {
      if (String(input).includes('/api/v1/tasks/task-1')) return jsonResponse(selected);
      return jsonResponse({ items: [baseTask], total: 1, page: 0, size: 50 });
    });

    render(<App />);
    await userEvent.click(await screen.findByText(baseTask.prompt!));

    expect(await screen.findByText('Ответ выбранной задачи')).toBeInTheDocument();
  });
});
