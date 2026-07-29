import { FormEvent, useState } from 'react';

export function TaskCreateForm({ onCreate }: { onCreate(prompt: string): Promise<void> }) {
  const [prompt, setPrompt] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      await onCreate(prompt);
      setPrompt('');
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit}>
      <label>
        Prompt
        <textarea aria-label="Prompt" value={prompt} onChange={e => setPrompt(e.target.value)} />
      </label>
      <button disabled={busy || !prompt.trim()}>Create task</button>
    </form>
  );
}
