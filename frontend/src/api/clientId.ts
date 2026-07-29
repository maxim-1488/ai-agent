const CLIENT_ID_STORAGE_KEY = 'ai-agent-client-id';

export function getClientId(): string {
  const existing = localStorage.getItem(CLIENT_ID_STORAGE_KEY);
  if (existing) return existing;
  const generated = crypto.randomUUID ? crypto.randomUUID() : `client-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  localStorage.setItem(CLIENT_ID_STORAGE_KEY, generated);
  return generated;
}
