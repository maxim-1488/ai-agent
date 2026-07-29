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

interface ChatSession {
  id: string;
  taskIds: string[];
}

function sessionsStorageKey(clientId: string): string {
  return `ai-agent.chatSessions.${clientId}`;
}

function loadSessions(clientId: string): ChatSession[] {
  try {
    const raw = localStorage.getItem(sessionsStorageKey(clientId));
    if (!raw) return [];
    const parsed = JSON.parse(raw) as ChatSession[];
    return Array.isArray(parsed)
      ? parsed.filter(session => session.id && Array.isArray(session.taskIds) && session.taskIds.length > 0)
      : [];
  } catch {
    return [];
  }
}

function saveSessions(clientId: string, sessions: ChatSession[]) {
  localStorage.setItem(sessionsStorageKey(clientId), JSON.stringify(sessions));
}

export function ChatPage() {
  const clientId = useMemo(() => getClientId(), []);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [sessions, setSessions] = useState<ChatSession[]>(() => loadSessions(clientId));
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const { tasks, loading, upsertTask, refreshTasks } = useTasks(clientId);
  const taskState = useTask(clientId, upsertTask);
  const selectedTaskId = taskState.selectedTask?.id ?? null;
  const { setSelectedTask, refreshSelectedTask, setError } = taskState;

  useEffect(() => {
    saveSessions(clientId, sessions);
  }, [clientId, sessions]);

  const taskById = useMemo(() => new Map(tasks.map(task => [task.id, task])), [tasks]);
  const activeSession = sessions.find(session => session.id === activeSessionId) ?? null;
  const chatTasks = activeSession?.taskIds
    .map(taskId => taskById.get(taskId) ?? (taskState.selectedTask?.id === taskId ? taskState.selectedTask : null))
    .filter((task): task is Task => Boolean(task)) ?? [];

  const sidebarTasks = useMemo(() => {
    const hiddenTaskIds = new Set<string>();
    const taskToSession = new Map<string, ChatSession>();
    sessions.forEach(session => {
      session.taskIds.forEach((taskId, index) => {
        taskToSession.set(taskId, session);
        if (index > 0) hiddenTaskIds.add(taskId);
      });
    });

    return tasks
      .filter(task => !hiddenTaskIds.has(task.id))
      .map(task => {
        const session = taskToSession.get(task.id);
        if (!session) return task;
        const latestTask = [...session.taskIds].reverse()
          .map(taskId => taskById.get(taskId))
          .find((candidate): candidate is Task => Boolean(candidate));
        return latestTask ? { ...latestTask, id: task.id, prompt: task.prompt } : task;
      });
  }, [sessions, taskById, tasks]);

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
    const session = sessions.find(item => item.taskIds.includes(taskId));
    const nextTaskIds = session?.taskIds ?? [taskId];
    const latestTaskId = nextTaskIds[nextTaskIds.length - 1];
    setActiveSessionId(session?.id ?? taskId);
    if (!session) {
      setSessions(current => current.some(item => item.id === taskId) ? current : [{ id: taskId, taskIds: [taskId] }, ...current]);
    }
    await taskState.selectTask(latestTaskId);
    setSidebarOpen(false);
  }

  async function submitPrompt(prompt: string) {
    const task = await taskState.submitPrompt(prompt);
    if (task) {
      setActiveSessionId(currentActiveSessionId => {
        const sessionId = currentActiveSessionId ?? task.id;
        setSessions(current => {
          const existing = current.find(session => session.id === sessionId);
          if (!existing) return [{ id: sessionId, taskIds: [task.id] }, ...current];
          if (existing.taskIds.includes(task.id)) return current;
          return current.map(session => session.id === sessionId
            ? { ...session, taskIds: [...session.taskIds, task.id] }
            : session);
        });
        return sessionId;
      });
    }
    return Boolean(task);
  }

  function newTask() {
    setActiveSessionId(null);
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
          activeTaskId={activeSession?.taskIds[0] ?? selectedTaskId}
          open={sidebarOpen}
          onClose={() => setSidebarOpen(false)}
          onNewTask={newTask}
          onSelectTask={selectTask}
        />
      }
    >
      <ChatView
        tasks={chatTasks}
        creating={taskState.creating}
        cancelling={taskState.cancelling}
        apiError={taskState.error}
        onSubmit={submitPrompt}
        onCancel={() => void taskState.stopTask()}
      />
    </MainLayout>
  );
}
