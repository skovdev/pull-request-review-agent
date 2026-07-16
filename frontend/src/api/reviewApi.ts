import type { ReviewResult, StartReviewRequest } from '../types';

export class ReviewApiError extends Error {}

export async function startReview(request: StartReviewRequest): Promise<ReviewResult> {
  const response = await fetch('/api/reviews', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    const message = body?.error ?? `Request failed with status ${response.status}`;
    throw new ReviewApiError(message);
  }

  return response.json();
}
