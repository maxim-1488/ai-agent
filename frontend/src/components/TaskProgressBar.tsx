export function TaskProgressBar({ progress }: { progress: number }) {
  return <progress aria-label="Task progress" max={100} value={progress}>{progress}%</progress>;
}
