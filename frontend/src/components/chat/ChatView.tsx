import type { UserFacingError } from '../../model/apiError';
import type { Task } from '../../model/task';
import { AgentMessage } from './AgentMessage';
import { EmptyChat } from './EmptyChat';
import { MessageComposer } from './MessageComposer';
import { UserMessage } from './UserMessage';

export function ChatView({
  tasks,
  creating,
  cancelling,
  apiError,
  onSubmit,
  onCancel
}: {
  tasks: Task[];
  creating: boolean;
  cancelling: boolean;
  apiError: UserFacingError | null;
  onSubmit(prompt: string): Promise<boolean>;
  onCancel(): void;
}) {
  const hasMessages = tasks.length > 0;
  const activeTask = tasks[tasks.length - 1] ?? null;

  return (
    <main className={`chat${!hasMessages ? ' chat--empty' : ''}`}>
      {!hasMessages ? (
        <div className="chat__empty-state">
          <div className="chat__content">
            <EmptyChat />
          </div>
          <div className="chat__composer chat__composer--empty">
            <MessageComposer onSubmit={onSubmit} disabled={creating} />
          </div>
          {apiError && <div className="chat-error chat-error--empty" role="alert">{apiError.message}</div>}
        </div>
      ) : (
        <>
          <div className="chat__scroll">
            <div className="chat__content">
              {tasks.map(task => (
                <div key={task.id}>
                  <UserMessage prompt={task.prompt} />
                  <AgentMessage
                    task={task}
                    onCancel={onCancel}
                    cancelling={activeTask?.id === task.id && cancelling}
                  />
                </div>
              ))}
              {apiError && <div className="chat-error" role="alert">{apiError.message}</div>}
            </div>
          </div>
          <div className="chat__composer">
            <MessageComposer onSubmit={onSubmit} disabled={creating} />
          </div>
        </>
      )}
    </main>
  );
}
