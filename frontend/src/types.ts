export type Recommendation = 'APPROVE' | 'COMMENT' | 'REQUEST_CHANGES';

export type FindingSeverity = 'BLOCKING' | 'MAJOR' | 'MINOR' | 'NIT';

export interface ReviewFinding {
  severity: FindingSeverity;
  file: string | null;
  line: number | null;
  title: string;
  description: string;
  suggestion: string | null;
}

export interface ReviewResult {
  summary: string;
  recommendation: Recommendation;
  findings: ReviewFinding[];
}

export interface StartReviewRequest {
  repositoryPath: string;
  baseBranch: string;
  reviewBranch?: string;
}
