import { useState } from 'react';
import { ReviewForm } from './components/ReviewForm';
import { ReviewResults } from './components/ReviewResults';
import { startReview, ReviewApiError } from './api/reviewApi';
import type { ReviewResult, StartReviewRequest } from './types';
import './App.css';

type Status = 'idle' | 'loading' | 'success' | 'error';

function App() {
  const [status, setStatus] = useState<Status>('idle');
  const [result, setResult] = useState<ReviewResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(request: StartReviewRequest) {
    setStatus('loading');
    setError(null);
    setResult(null);
    try {
      const reviewResult = await startReview(request);
      setResult(reviewResult);
      setStatus('success');
    } catch (err) {
      const message = err instanceof ReviewApiError ? err.message : 'Unexpected error while starting the review.';
      setError(message);
      setStatus('error');
    }
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>Pull Request Review Agent</h1>
        <p>Point it at a local git repository and let the AI review your changes.</p>
      </header>

      <ReviewForm onSubmit={handleSubmit} isLoading={status === 'loading'} />

      {status === 'loading' && (
        <div className="status-panel loading-panel">
          <span className="spinner" aria-hidden="true" />
          Running review, this may take a moment…
        </div>
      )}

      {status === 'error' && error && <div className="status-panel error-panel">{error}</div>}

      {status === 'success' && result && <ReviewResults result={result} />}
    </div>
  );
}

export default App;
