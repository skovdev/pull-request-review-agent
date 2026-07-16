import { useState } from 'react';
import type { FormEvent } from 'react';
import type { StartReviewRequest } from '../types';

interface ReviewFormProps {
  onSubmit: (request: StartReviewRequest) => void;
  isLoading: boolean;
}

export function ReviewForm({ onSubmit, isLoading }: ReviewFormProps) {
  const [repositoryPath, setRepositoryPath] = useState('');
  const [baseBranch, setBaseBranch] = useState('main');
  const [reviewBranch, setReviewBranch] = useState('');

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    onSubmit({
      repositoryPath: repositoryPath.trim(),
      baseBranch: baseBranch.trim(),
      reviewBranch: reviewBranch.trim() || undefined,
    });
  }

  return (
    <form className="review-form" onSubmit={handleSubmit}>
      <div className="field">
        <label htmlFor="repositoryPath">Repository path</label>
        <input
          id="repositoryPath"
          type="text"
          placeholder="/home/user/projects/my-repo"
          value={repositoryPath}
          onChange={(e) => setRepositoryPath(e.target.value)}
          required
          disabled={isLoading}
        />
      </div>

      <div className="field-row">
        <div className="field">
          <label htmlFor="baseBranch">Base branch</label>
          <input
            id="baseBranch"
            type="text"
            placeholder="main"
            value={baseBranch}
            onChange={(e) => setBaseBranch(e.target.value)}
            required
            disabled={isLoading}
          />
        </div>

        <div className="field">
          <label htmlFor="reviewBranch">
            Review branch <span className="optional">(optional)</span>
          </label>
          <input
            id="reviewBranch"
            type="text"
            placeholder="leave blank for working tree changes"
            value={reviewBranch}
            onChange={(e) => setReviewBranch(e.target.value)}
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
