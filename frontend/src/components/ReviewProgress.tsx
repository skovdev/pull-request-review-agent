interface ReviewProgressProps {
  messages: string[];
}

export function ReviewProgress({ messages }: ReviewProgressProps) {
  if (messages.length === 0) {
    return null;
  }

  return (
    <ul className="progress-log" aria-live="polite">
      {messages.map((message, index) => (
        <li key={index} className={index === messages.length - 1 ? 'progress-current' : 'progress-done'}>
          {message}
        </li>
      ))}
    </ul>
  );
}
