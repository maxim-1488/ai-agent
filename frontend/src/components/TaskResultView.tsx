export function TaskResultView({ result }: { result?: string | null }) {
  return result ? <section aria-label="Task result">{result}</section> : null;
}
