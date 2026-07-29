import { FormEvent, KeyboardEvent, useEffect, useRef, useState } from 'react';
import { PROMPT_MAX_LENGTH } from '../../api/taskApi';

export function MessageComposer({ onSubmit, disabled }: { onSubmit(prompt: string): Promise<boolean> | boolean; disabled: boolean }) {
  const [prompt, setPrompt] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const trimmed = prompt.trim();

  useEffect(() => {
    const element = textareaRef.current;
    if (!element) return;
    element.style.height = 'auto';
    element.style.height = `${Math.min(element.scrollHeight, 220)}px`;
  }, [prompt]);

  async function submit(event?: FormEvent) {
    event?.preventDefault();
    if (!trimmed || disabled || trimmed.length > PROMPT_MAX_LENGTH) return;
    const accepted = await onSubmit(trimmed);
    if (accepted) setPrompt('');
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      void submit();
    }
  }

  return (
    <form className="composer" onSubmit={submit}>
      <textarea
        ref={textareaRef}
        aria-label="Введите задание для AI"
        placeholder="Введите задание для AI..."
        value={prompt}
        maxLength={PROMPT_MAX_LENGTH}
        disabled={disabled}
        onChange={event => setPrompt(event.target.value)}
        onKeyDown={onKeyDown}
        rows={1}
      />
      <button type="submit" className="send-button" disabled={disabled || !trimmed || trimmed.length > PROMPT_MAX_LENGTH} aria-label="Отправить задание">
        ↑
      </button>
      <div className="composer__meta">{prompt.length}/{PROMPT_MAX_LENGTH}</div>
    </form>
  );
}
