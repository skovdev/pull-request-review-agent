import { useState } from 'react';
import type { FormEvent } from 'react';
import type { StartReviewRequest } from '../types';

interface ReviewFormProps {
  onSubmit: (request: StartReviewRequest) => void;
  isLoading: boolean;
}

export function ReviewForm({ onSubmit, isLoading }: ReviewFormProps) {
  const [owner, setOwner] = useState('');
  const [repo, setRepo] = useState('');
  const [pullNumber, setPullNumber] = useState('');

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    onSubmit({
      owner: owner.trim(),
      repo: repo.trim(),
      pullNumber: Number(pullNumber),
    });
  }

  return (
    <form className="review-form" onSubmit={handleSubmit}>
      <div className="field-row">
        <div className="field">
          <label htmlFor="owner">Owner</label>
          <input
            id="owner"
            type="text"
            placeholder="spring-projects"
            value={owner}
            onChange={(e) => setOwner(e.target.value)}
            required
            disabled={isLoading}
          />
        </div>

        <div className="field">
          <label htmlFor="repo">Repository</label>
          <input
            id="repo"
            type="text"
            placeholder="spring-framework"
            value={repo}
            onChange={(e) => setRepo(e.target.value)}
            required
            disabled={isLoading}
          />
        </div>

        <div className="field">
          <label htmlFor="pullNumber">Pull request #</label>
          <input
            id="pullNumber"
            type="number"
            min="1"
            placeholder="123"
            value={pullNumber}
            onChange={(e) => setPullNumber(e.target.value)}
            required
            disabled={isLoading}
          />
        </div>
      </div>

      <button type="submit" disabled={isLoading}>
        {isLoading ? 'Reviewing…' : 'Start review'}
      </button>
    </form>
  );
}
