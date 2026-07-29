export function UserMessage({ prompt }: { prompt?: string | null }) {
  return (
    <article className="message message--user">
      <div className="message__author">Вы</div>
      <div className="message__content">{prompt || 'Задание недоступно в ответе backend.'}</div>
    </article>
  );
}
