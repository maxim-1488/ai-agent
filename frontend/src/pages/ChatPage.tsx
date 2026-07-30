import { useCallback, useEffect, useMemo, useState } from 'react';
import { getClientId } from '../api/clientId';
import { ChatView } from '../components/chat/ChatView';
import { MainLayout } from '../components/layout/MainLayout';
import { Sidebar } from '../components/layout/Sidebar';
import { useTask } from '../hooks/useTask';
import { useTasks } from '../hooks/useTasks';
import { useTaskWebSocket } from '../hooks/useTaskWebSocket';
import { sanitizeBackendMessage } from '../model/apiError';
import type { Task } from '../model/task';

interface TaskThread {
  id: string;
  taskIds: string[];
}

function taskThreadsStorageKey(clientId: string): string {
  return `ai-agent.taskThreads.${clientId}`;
}

function legacyChatSessionsStorageKey(clientId: string): string {
  return `ai-agent.chatSessions.${clientId}`;
}

function loadTaskThreads(clientId: string): TaskThread[] {
  try {
    const raw = localStorage.getItem(taskThreadsStorageKey(clientId))
      ?? localStorage.getItem(legacyChatSessionsStorageKey(clientId));
    if (!raw) return [];
    const parsed = JSON.parse(raw) as TaskThread[];
    return Array.isArray(parsed)
      ? parsed.filter(thread => thread.id && Array.isArray(thread.taskIds) && thread.taskIds.length > 0)
      : [];
  } catch {
    return [];
  }
}

function saveTaskThreads(clientId: string, threads: TaskThread[]) {
  localStorage.setItem(taskThreadsStorageKey(clientId), JSON.stringify(threads));
}

export function ChatPage() {
  const clientId = useMemo(() => getClientId(), []);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [taskThreads, setTaskThreads] = useState<TaskThread[]>(() => loadTaskThreads(clientId));
  const [activeThreadId, setActiveThreadId] = useState<string | null>(null);
  const { tasks, loading, upsertTask, refreshTasks } = useTasks(clientId);
  const taskState = useTask(clientId, upsertTask);
  const selectedTaskId = taskState.selectedTask?.id ?? null;
  const { setSelectedTask, refreshSelectedTask, setError } = taskState;

  useEffect(() => {
    saveTaskThreads(clientId, taskThreads);
  }, [clientId, taskThreads]);

  const taskById = useMemo(() => new Map(tasks.map(task => [task.id, task])), [tasks]);
  const activeThread = taskThreads.find(thread => thread.id === activeThreadId) ?? null;
  const threadTasks = activeThread?.taskIds
    .map(taskId => taskById.get(taskId) ?? (taskState.selectedTask?.id === taskId ? taskState.selectedTask : null))
    .filter((task): task is Task => Boolean(task)) ?? [];

  const sidebarTasks = useMemo(() => {
    const hiddenTaskIds = new Set<string>();
    const taskToThread = new Map<string, TaskThread>();
    taskThreads.forEach(thread => {
      thread.taskIds.forEach((taskId, index) => {
        taskToThread.set(taskId, thread);
        if (index > 0) hiddenTaskIds.add(taskId);
      });
    });

    return tasks
      .filter(task => !hiddenTaskIds.has(task.id))
      .map(task => {
        const thread = taskToThread.get(task.id);
        if (!thread) return task;
        const latestTask = [...thread.taskIds].reverse()
          .map(taskId => taskById.get(taskId))
          .find((candidate): candidate is Task => Boolean(candidate));
        return latestTask ? { ...latestTask, id: task.id, prompt: task.prompt } : task;
      });
  }, [taskThreads, taskById, tasks]);

  const applyRealtimeTask = useCallback((task: Task) => {
    setSelectedTask(task);
  }, [setSelectedTask]);

  const handleReconnect = useCallback(() => {
    void refreshSelectedTask();
    void refreshTasks();
  }, [refreshSelectedTask, refreshTasks]);

  const handleWsError = useCallback((message: string) => {
    setError({ message: sanitizeBackendMessage(message), fieldErrors: {} });
  }, [setError]);

  const { connectionStatus } = useTaskWebSocket({
    clientId,
    selectedTaskId,
    onTask: applyRealtimeTask,
    onReconnect: handleReconnect,
    onError: handleWsError
  });

  async function selectTask(taskId: string) {
    const thread = taskThreads.find(item => item.taskIds.includes(taskId));
    const nextTaskIds = thread?.taskIds ?? [taskId];
    const latestTaskId = nextTaskIds[nextTaskIds.length - 1];
    setActiveThreadId(thread?.id ?? taskId);
    if (!thread) {
      setTaskThreads(current => current.some(item => item.id === taskId) ? current : [{ id: taskId, taskIds: [taskId] }, ...current]);
    }
    await taskState.selectTask(latestTaskId);
    setSidebarOpen(false);
  }

  async function submitPrompt(prompt: string) {
    const task = await taskState.submitPrompt(prompt);
    if (task) {
      setActiveThreadId(currentActiveThreadId => {
        const threadId = currentActiveThreadId ?? task.id;
        setTaskThreads(current => {
          const existing = current.find(thread => thread.id === threadId);
          if (!existing) return [{ id: threadId, taskIds: [task.id] }, ...current];
          if (existing.taskIds.includes(task.id)) return current;
          return current.map(thread => thread.id === threadId
            ? { ...thread, taskIds: [...thread.taskIds, task.id] }
            : thread);
        });
        return threadId;
      });
    }
    return Boolean(task);
  }

  function newTask() {
    setActiveThreadId(null);
    setSelectedTask(null);
    setSidebarOpen(false);
  }

  return (
    <MainLayout
      connectionStatus={connectionStatus}
      onOpenSidebar={() => setSidebarOpen(true)}
      sidebar={
        <Sidebar
          tasks={sidebarTasks}
          loading={loading}
          activeTaskId={activeThread?.taskIds[0] ?? selectedTaskId}
          open={sidebarOpen}
          onClose={() => setSidebarOpen(false)}
          onNewTask={newTask}
          onSelectTask={selectTask}
        />
      }
    >
      <ChatView
        tasks={threadTasks}
        creating={taskState.creating}
        cancelling={taskState.cancelling}
        apiError={taskState.error}
        onSubmit={submitPrompt}
        onCancel={() => void taskState.stopTask()}
      />
    </MainLayout>
  );
}
