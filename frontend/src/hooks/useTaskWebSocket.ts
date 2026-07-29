import { useEffect, useRef, useState } from 'react';
import type { ConnectionStatus, TaskEvent } from '../model/taskEvent';
import type { Task } from '../model/task';
import { TaskWebSocket } from '../websocket/taskWebSocket';

interface UseTaskWebSocketOptions {
  clientId: string;
  selectedTaskId: string | null;
  onTask(task: Task): void;
  onReconnect(): void;
  onError(message: string): void;
}

export function useTaskWebSocket({ clientId, selectedTaskId, onTask, onReconnect, onError }: UseTaskWebSocketOptions) {
  const [status, setStatus] = useState<ConnectionStatus>('disconnected');
  const socketRef = useRef<TaskWebSocket | null>(null);
  const previousTaskIdRef = useRef<string | null>(null);
  const onTaskRef = useRef(onTask);
  const onReconnectRef = useRef(onReconnect);
  const onErrorRef = useRef(onError);

  useEffect(() => {
    onTaskRef.current = onTask;
    onReconnectRef.current = onReconnect;
    onErrorRef.current = onError;
  }, [onError, onReconnect, onTask]);

  useEffect(() => {
    const socket = new TaskWebSocket({
      clientId,
      onStatus: setStatus,
      onReconnect: () => onReconnectRef.current(),
      onEvent: (event: TaskEvent) => {
        if (event.type === 'ERROR') {
          onErrorRef.current(event.message ?? 'WebSocket вернул ошибку.');
          return;
        }
        if (event.task) onTaskRef.current(event.task);
      }
    });
    socket.connect();
    socketRef.current = socket;
    return () => {
      socket.close();
      socketRef.current = null;
    };
  }, [clientId]);

  useEffect(() => {
    const previousTaskId = previousTaskIdRef.current;
    if (previousTaskId && previousTaskId !== selectedTaskId) socketRef.current?.unsubscribe(previousTaskId);
    if (selectedTaskId) socketRef.current?.subscribe(selectedTaskId);
    previousTaskIdRef.current = selectedTaskId;
  }, [selectedTaskId]);

  return { connectionStatus: status };
}
