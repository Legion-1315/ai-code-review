import { Link, useParams } from "react-router-dom";
import { useReview } from "../api/hooks";
import Spinner from "../components/Spinner";
import {
  CategoryBadge,
  ScoreBadge,
  SeverityBadge,
  StatusBadge,
} from "../components/Badges";
import type { Issue } from "../api/types";

export default function ReviewDetailPage() {
  const { id } = useParams();
  const reviewId = Number(id);
  const { data: review, isLoading, isError } = useReview(reviewId);

  if (isLoading) return <Spinner label="Loading review…" />;
  if (isError || !review)
    return <p className="text-red-600">Failed to load review.</p>;

  const processing = review.status === "PENDING" || review.status === "IN_PROGRESS";

  return (
    <div className="space-y-6">
      <div>
        <Link to="/reviews" className="text-sm text-brand-600 hover:underline">
          ← Back to reviews
        </Link>
      </div>

      <header className="card">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="text-xl font-bold">
              {review.prNumber ? `#${review.prNumber} ` : ""}
              {review.prTitle}
            </h1>
            <p className="text-sm text-slate-500 mt-1">
              {review.repositoryName} · by {review.author}
            </p>
            <div className="flex items-center gap-2 mt-3">
              <StatusBadge status={review.status} />
              {!review.usedRealAi && review.status === "COMPLETED" && (
                <span className="text-xs text-slate-400">
                  heuristic mode (set ANTHROPIC_API_KEY for full AI)
                </span>
              )}
              {review.contextFiles > 0 && (
                <span
                  className="text-xs px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200"
                  title="Full file contents from the repository were provided to the model alongside the diff"
                >
                  repo context: {review.contextFiles} file
                  {review.contextFiles === 1 ? "" : "s"}
                </span>
              )}
              {review.unanchoredFindings > 0 && (
                <span
                  className="text-xs px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200"
                  title="Findings whose line numbers did not exist in the diff were demoted to file level"
                >
                  {review.unanchoredFindings} anchor
                  {review.unanchoredFindings === 1 ? "" : "s"} demoted
                </span>
              )}
            </div>
          </div>
          <div className="text-right">
            <div className="text-xs text-slate-400 mb-1">Overall score</div>
            <ScoreBadge score={review.overallScore} />
          </div>
        </div>

        {review.summary && (
          <p className="mt-4 text-sm text-slate-700 bg-slate-50 rounded-md p-3">
            {review.summary}
          </p>
        )}
        {review.errorMessage && (
          <p className="mt-4 text-sm text-red-700 bg-red-50 rounded-md p-3">
            {review.errorMessage}
          </p>
        )}
      </header>

      {processing && <Spinner label="AI review in progress — this updates automatically…" />}

      <section className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
        <div>
          <h2 className="font-semibold mb-3">
            Findings{" "}
            <span className="text-slate-400 font-normal">({review.issues.length})</span>
          </h2>
          {review.issues.length === 0 ? (
            <div className="card text-slate-500 text-sm">
              {processing ? "Analyzing…" : "No issues found. Clean diff! 🎉"}
            </div>
          ) : (
            <ul className="space-y-3">
              {review.issues.map((issue) => (
                <IssueCard key={issue.id} issue={issue} />
              ))}
            </ul>
          )}
        </div>

        <div>
          <h2 className="font-semibold mb-3">Diff</h2>
          <pre className="card p-0 overflow-auto text-xs leading-relaxed max-h-[600px]">
            <code className="block">
              {review.diff.split("\n").map((line, i) => (
                <DiffLine key={i} line={line} />
              ))}
            </code>
          </pre>
        </div>
      </section>
    </div>
  );
}

function IssueCard({ issue }: { issue: Issue }) {
  return (
    <li className="card">
      <div className="flex items-center gap-2 mb-2 flex-wrap">
        <SeverityBadge severity={issue.severity} />
        <CategoryBadge category={issue.category} />
        <span className="text-xs text-slate-500 font-mono">
          {issue.filePath}
          {issue.lineNumber ? `:${issue.lineNumber}` : ""}
        </span>
      </div>
      <p className="text-sm text-slate-800">{issue.message}</p>
      {issue.suggestion && (
        <p className="text-sm text-slate-600 mt-2 border-l-2 border-brand-300 pl-3">
          <span className="font-medium text-brand-700">Suggestion: </span>
          {issue.suggestion}
        </p>
      )}
    </li>
  );
}

function DiffLine({ line }: { line: string }) {
  let cls = "text-slate-700";
  if (line.startsWith("+") && !line.startsWith("+++")) cls = "bg-green-50 text-green-800";
  else if (line.startsWith("-") && !line.startsWith("---")) cls = "bg-red-50 text-red-800";
  else if (line.startsWith("@@")) cls = "text-brand-600 font-semibold";
  return <div className={`px-4 ${cls}`}>{line || " "}</div>;
}
