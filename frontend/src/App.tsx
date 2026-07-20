import { useState } from 'react';
import { ReviewForm } from './components/ReviewForm';
import { ReviewResults } from './components/ReviewResults';
import { ReviewProgress } from './components/ReviewProgress';
import { streamReview } from './api/reviewApi';
import type { ReviewResult, StartReviewRequest } from './types';
import './App.css';

type Status = 'idle' | 'loading' | 'success' | 'error';

function App() {
  const [status, setStatus] = useState<Status>('idle');
  const [result, setResult] = useState<ReviewResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [progress, setProgress] = useState<string[]>([]);

  async function handleSubmit(request: StartReviewRequest) {
    setStatus('loading');
    setError(null);
    setResult(null);
    setProgress([]);

    await streamReview(request, {
      onProgress: (message) => setProgress((previous) => [...previous, message]),
      onResult: (reviewResult) => {
        setResult(reviewResult);
        setStatus('success');
      },
      onError: (message) => {
        setError(message);
        setStatus('error');
      },
    });
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
          <div className="loading-header">
            <span className="spinner" aria-hidden="true" />
            {progress.length > 0 ? progress[progress.length - 1] : 'Starting review…'}
          </div>
          <ReviewProgress messages={progress} />
        </div>
      )}

      {status === 'error' && error && <div className="status-panel error-panel">{error}</div>}

      {status === 'success' && result && <ReviewResults result={result} />}
    </div>
  );
}

export default App;
