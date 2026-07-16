import type { FindingSeverity, ReviewResult } from '../types';
import { RecommendationBadge } from './RecommendationBadge';
import { FindingCard } from './FindingCard';

const SEVERITY_ORDER: FindingSeverity[] = ['BLOCKING', 'MAJOR', 'MINOR', 'NIT'];

export function ReviewResults({ result }: { result: ReviewResult }) {
  const findings = [...result.findings].sort(
    (a, b) => SEVERITY_ORDER.indexOf(a.severity) - SEVERITY_ORDER.indexOf(b.severity),
  );

  return (
    <section className="review-results">
      <div className="results-header">
        <h2>Review summary</h2>
        <RecommendationBadge recommendation={result.recommendation} />
      </div>

      <p className="summary-text">{result.summary}</p>

      {findings.length === 0 ? (
        <p className="no-findings">No findings reported.</p>
      ) : (
        <ul className="findings-list">
          {findings.map((finding, index) => (
            <FindingCard key={index} finding={finding} />
          ))}
        </ul>
      )}
    </section>
  );
}
