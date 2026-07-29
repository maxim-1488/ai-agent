export function ClientIdInput({ value, onChange }: { value: string; onChange(value: string): void }) {
  return (
    <label>
      Client ID
      <input aria-label="Client ID" value={value} onChange={e => onChange(e.target.value)} />
    </label>
  );
}
