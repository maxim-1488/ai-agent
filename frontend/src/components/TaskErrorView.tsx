export function TaskErrorView({ error }: { error?: string | null }) {
  return error ? <section role="alert">{error}</section> : null;
}
