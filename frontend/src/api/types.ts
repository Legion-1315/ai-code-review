export type ReviewStatus = "PENDING" | "IN_PROGRESS" | "COMPLETED" | "FAILED";

export type Severity = "INFO" | "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export type IssueCategory =
  | "SECURITY"
  | "CODE_QUALITY"
  | "PERFORMANCE"
  | "BEST_PRACTICE"
  | "TEST_COVERAGE";

export interface AuthResponse {
  token: string;
  userId: number;
  name: string;
  email: string;
}

export interface Repository {
  id: number;
  fullName: string;
  minScoreThreshold: number;
  createdAt: string;
}

export interface Issue {
  id: number;
  filePath: string;
  lineNumber: number | null;
  category: IssueCategory;
  severity: Severity;
  message: string;
  suggestion: string | null;
}

export interface ReviewSummary {
  id: number;
  prTitle: string;
  prNumber: number | null;
  author: string;
  repositoryName: string;
  status: ReviewStatus;
  overallScore: number | null;
  issueCount: number;
  createdAt: string;
}

export interface ReviewDetail {
  id: number;
  prTitle: string;
  prNumber: number | null;
  author: string;
  repositoryName: string;
  diff: string;
  status: ReviewStatus;
  overallScore: number | null;
  summary: string | null;
  usedRealAi: boolean;
  unanchoredFindings: number;
  contextFiles: number;
  errorMessage: string | null;
  issues: Issue[];
  createdAt: string;
  completedAt: string | null;
}

export interface ScorePoint {
  reviewId: number;
  label: string;
  score: number;
}

export interface DashboardStats {
  totalReviews: number;
  completedReviews: number;
  inProgressReviews: number;
  averageScore: number;
  repositoryCount: number;
  issuesByCategory: Record<IssueCategory, number>;
  scoreTrend: ScorePoint[];
}

export interface EvalCategoryScore {
  category: IssueCategory;
  tp: number;
  fn: number;
  fp: number;
}

export interface EvalEngineScore {
  engine: string;
  truePositives: number;
  falseNegatives: number;
  falsePositives: number;
  precision: number;
  recall: number;
  f1: number;
  heuristicDetectableRecall: number;
  semanticRecall: number;
  cleanFalsePositives: number;
  demotedAnchors: number;
  categories: EvalCategoryScore[];
}

export interface EvalReport {
  generatedAt: string;
  dataset: {
    cases: number;
    buggyCases: number;
    cleanCases: number;
    labeledBugs: number;
  };
  methodology: string;
  engines: EvalEngineScore[];
}
