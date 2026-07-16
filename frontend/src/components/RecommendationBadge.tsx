import type { Recommendation } from '../types';

const LABELS: Record<Recommendation, string> = {
  APPROVE: 'Approve',
  COMMENT: 'Comment',
  REQUEST_CHANGES: 'Request changes',
};

export function RecommendationBadge({ recommendation }: { recommendation: Recommendation }) {
  return (
    <span className={`recommendation-badge recommendation-${recommendation.toLowerCase()}`}>
      {LABELS[recommendation]}
    </span>
  );
}
