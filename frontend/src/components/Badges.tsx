import type { ReviewStatus, Severity } from "../api/types";

const severityStyles: Record<Severity, string> = {
  INFO: "bg-slate-100 text-slate-600",
  LOW: "bg-sky-100 text-sky-700",
  MEDIUM: "bg-amber-100 text-amber-700",
  HIGH: "bg-orange-100 text-orange-700",
  CRITICAL: "bg-red-100 text-red-700",
};

export function SeverityBadge({ severity }: { severity: Severity }) {
  return (
    <span
      className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${severityStyles[severity]}`}
    >
      {severity}
    </span>
  );
}

const statusStyles: Record<ReviewStatus, string> = {
  PENDING: "bg-slate-100 text-slate-600",
  IN_PROGRESS: "bg-blue-100 text-blue-700",
  COMPLETED: "bg-green-100 text-green-700",
  FAILED: "bg-red-100 text-red-700",
};

export function StatusBadge({ status }: { status: ReviewStatus }) {
  const label = status.replace("_", " ").toLowerCase();
  return (
    <span
      className={`inline-block px-2 py-0.5 rounded text-xs font-semibold capitalize ${statusStyles[status]}`}
    >
      {label}
    </span>
  );
}

export function ScoreBadge({ score }: { score: number | null }) {
  if (score === null || score === undefined) {
    return <span className="text-slate-400 text-sm">—</span>;
  }
  const color =
    score >= 80
      ? "bg-green-100 text-green-700"
      : score >= 60
        ? "bg-amber-100 text-amber-700"
        : "bg-red-100 text-red-700";
  return (
    <span className={`inline-block px-2.5 py-1 rounded-full text-sm font-bold ${color}`}>
      {score}/100
    </span>
  );
}

export function CategoryBadge({ category }: { category: string }) {
  return (
    <span className="inline-block px-2 py-0.5 rounded text-xs font-medium bg-brand-50 text-brand-700">
      {category.replace("_", " ")}
    </span>
  );
}
