import type { ReviewFinding } from '../types';

const SEVERITY_LABELS: Record<ReviewFinding['severity'], string> = {
  BLOCKING: 'Blocking',
  MAJOR: 'Major',
  MINOR: 'Minor',
  NIT: 'Nit',
};

export function FindingCard({ finding }: { finding: ReviewFinding }) {
  return (
    <li className={`finding-card severity-${finding.severity.toLowerCase()}`}>
      <div className="finding-header">
        <span className="severity-tag">{SEVERITY_LABELS[finding.severity]}</span>
        <span className="finding-title">{finding.title}</span>
      </div>

      {finding.file && (
        <div className="finding-location">
          {finding.file}
          {finding.line != null ? `:${finding.line}` : ''}
        </div>
      )}

      <p className="finding-description">{finding.description}</p>

      {finding.suggestion && (
        <div className="finding-suggestion">
          <span className="suggestion-label">Suggestion</span>
          <p>{finding.suggestion}</p>
        </div>
      )}
    </li>
  );
}
