import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Link } from "react-router-dom";
import { useDashboardStats } from "../api/hooks";
import Spinner from "../components/Spinner";
import type { IssueCategory } from "../api/types";

const CATEGORY_LABELS: Record<IssueCategory, string> = {
  SECURITY: "Security",
  CODE_QUALITY: "Code Quality",
  PERFORMANCE: "Performance",
  BEST_PRACTICE: "Best Practice",
  TEST_COVERAGE: "Test Coverage",
};

export default function DashboardPage() {
  const { data, isLoading, isError } = useDashboardStats();

  if (isLoading) return <Spinner label="Loading dashboard…" />;
  if (isError || !data)
    return <p className="text-red-600">Failed to load dashboard.</p>;

  const categoryData = (
    Object.entries(data.issuesByCategory) as [IssueCategory, number][]
  ).map(([category, count]) => ({
    name: CATEGORY_LABELS[category],
    count,
  }));

  return (
    <div className="space-y-8">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Dashboard</h1>
          <p className="text-slate-500 text-sm">
            Overview of your automated code reviews
          </p>
        </div>
        <Link to="/reviews/new" className="btn-primary">
          + New Review
        </Link>
      </header>

      <section className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="Total Reviews" value={data.totalReviews} />
        <StatCard label="Completed" value={data.completedReviews} accent="green" />
        <StatCard
          label="In Progress"
          value={data.inProgressReviews}
          accent="blue"
        />
        <StatCard
          label="Avg. Score"
          value={data.averageScore ? `${data.averageScore}` : "—"}
          accent="brand"
        />
      </section>

      <section className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="card">
          <h2 className="font-semibold mb-4">Score Trend</h2>
          {data.scoreTrend.length === 0 ? (
            <EmptyChart message="No completed reviews yet." />
          ) : (
            <ResponsiveContainer width="100%" height={260}>
              <LineChart data={data.scoreTrend}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="label" tick={{ fontSize: 12 }} />
                <YAxis domain={[0, 100]} tick={{ fontSize: 12 }} />
                <Tooltip />
                <Line
                  type="monotone"
                  dataKey="score"
                  stroke="#4f46e5"
                  strokeWidth={2}
                  dot={{ r: 3 }}
                />
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="card">
          <h2 className="font-semibold mb-4">Issues by Category</h2>
          {categoryData.every((c) => c.count === 0) ? (
            <EmptyChart message="No issues recorded yet." />
          ) : (
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={categoryData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="name" tick={{ fontSize: 11 }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
                <Tooltip />
                <Bar dataKey="count" fill="#6366f1" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </section>
    </div>
  );
}

function StatCard({
  label,
  value,
  accent = "slate",
}: {
  label: string;
  value: string | number;
  accent?: "slate" | "green" | "blue" | "brand";
}) {
  const accents: Record<string, string> = {
    slate: "text-slate-800",
    green: "text-green-600",
    blue: "text-blue-600",
    brand: "text-brand-600",
  };
  return (
    <div className="card">
      <div className="text-sm text-slate-500">{label}</div>
      <div className={`text-3xl font-bold mt-1 ${accents[accent]}`}>{value}</div>
    </div>
  );
}

function EmptyChart({ message }: { message: string }) {
  return (
    <div className="h-[260px] flex items-center justify-center text-slate-400 text-sm">
      {message}
    </div>
  );
}
