export function LoadingIndicator({ label = 'Загрузка' }: { label?: string }) {
  return <div className="loading-indicator" aria-label={label} />;
}
