import { Link } from "react-router-dom";
import { useReviews } from "../api/hooks";
import Spinner from "../components/Spinner";
import { ScoreBadge, StatusBadge } from "../components/Badges";

export default function ReviewsPage() {
  const { data, isLoading, isError } = useReviews();

  if (isLoading) return <Spinner label="Loading reviews…" />;
  if (isError || !data) return <p className="text-red-600">Failed to load reviews.</p>;

  return (
    <div className="space-y-6">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Reviews</h1>
          <p className="text-slate-500 text-sm">All pull requests analyzed by the AI</p>
        </div>
        <Link to="/reviews/new" className="btn-primary">
          + New Review
        </Link>
      </header>

      {data.length === 0 ? (
        <div className="card text-center text-slate-500 py-12">
          No reviews yet. Submit a diff to get started.
        </div>
      ) : (
        <div className="card p-0 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-slate-500 text-left">
              <tr>
                <th className="px-4 py-3 font-medium">Title</th>
                <th className="px-4 py-3 font-medium">Repository</th>
                <th className="px-4 py-3 font-medium">Author</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium">Issues</th>
                <th className="px-4 py-3 font-medium">Score</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.map((review) => (
                <tr key={review.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3">
                    <Link
                      to={`/reviews/${review.id}`}
                      className="font-medium text-brand-700 hover:underline"
                    >
                      {review.prNumber ? `#${review.prNumber} ` : ""}
                      {review.prTitle}
                    </Link>
                  </td>
                  <td className="px-4 py-3 text-slate-600">{review.repositoryName}</td>
                  <td className="px-4 py-3 text-slate-600">{review.author}</td>
                  <td className="px-4 py-3">
                    <StatusBadge status={review.status} />
                  </td>
                  <td className="px-4 py-3 text-slate-600">{review.issueCount}</td>
                  <td className="px-4 py-3">
                    <ScoreBadge score={review.overallScore} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
