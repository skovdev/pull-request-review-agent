import type { ReviewResult, StartReviewRequest } from '../types';

export interface ReviewStreamHandlers {
  onProgress: (message: string) => void;
  onResult: (result: ReviewResult) => void;
  onError: (message: string) => void;
}

/**
 * Starts a review and streams progress as the agent works. The backend responds with
 * Server-Sent Events rather than one JSON blob, so `fetch` + a manual reader is used
 * instead of `EventSource` (which can't send a POST body).
 */
export async function streamReview(request: StartReviewRequest, handlers: ReviewStreamHandlers): Promise<void> {
  let response: Response;
  try {
    response = await fetch('/api/reviews', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify(request),
    });
  } catch {
    handlers.onError('Could not reach the review service.');
    return;
  }

  if (!response.ok || !response.body) {
    handlers.onError(`Request failed with status ${response.status}`);
    return;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  for (;;) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });

    let separatorIndex;
    while ((separatorIndex = buffer.indexOf('\n\n')) !== -1) {
      const rawEvent = buffer.slice(0, separatorIndex);
      buffer = buffer.slice(separatorIndex + 2);
      dispatchEvent(rawEvent, handlers);
    }
  }
}

function dispatchEvent(rawEvent: string, handlers: ReviewStreamHandlers) {
  let eventName = 'message';
  const dataLines: string[] = [];

  for (const line of rawEvent.split('\n')) {
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trim());
    }
  }
  if (dataLines.length === 0) {
    return;
  }
  const data = dataLines.join('\n');

  switch (eventName) {
    case 'progress':
      handlers.onProgress(data);
      break;
    case 'result':
      handlers.onResult(JSON.parse(data) as ReviewResult);
      break;
    case 'error':
      handlers.onError(data);
      break;
  }
}
