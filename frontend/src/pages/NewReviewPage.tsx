import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useRepositories, useSubmitReview } from "../api/hooks";
import { errorMessage } from "../api/client";

const SAMPLE_DIFF = `diff --git a/src/UserService.java b/src/UserService.java
--- a/src/UserService.java
+++ b/src/UserService.java
@@ -10,6 +10,12 @@ public class UserService {
+    private static final String API_KEY = "sk-live-abc123secret";
+
+    public User findByName(String name) {
+        String sql = "SELECT * FROM users WHERE name = '" + name + "'";
+        return jdbc.queryForObject(sql, User.class);
+    }
`;

export default function NewReviewPage() {
  const navigate = useNavigate();
  const { data: repositories } = useRepositories();
  const submit = useSubmitReview();

  const [title, setTitle] = useState("");
  const [author, setAuthor] = useState("");
  const [repositoryId, setRepositoryId] = useState<string>("");
  const [diff, setDiff] = useState("");
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      const review = await submit.mutateAsync({
        title,
        author: author || undefined,
        repositoryId: repositoryId ? Number(repositoryId) : null,
        diff,
      });
      navigate(`/reviews/${review.id}`);
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  return (
    <div className="space-y-6 max-w-3xl">
      <header>
        <h1 className="text-2xl font-bold">New Review</h1>
        <p className="text-slate-500 text-sm">
          Paste a unified diff and the AI engine will analyze it.
        </p>
      </header>

      <form onSubmit={handleSubmit} className="card space-y-4">
        <label className="block">
          <span className="block text-sm font-medium text-slate-700 mb-1">Title</span>
          <input
            className="input"
            required
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Add user lookup endpoint"
          />
        </label>

        <div className="grid grid-cols-2 gap-4">
          <label className="block">
            <span className="block text-sm font-medium text-slate-700 mb-1">
              Author <span className="text-slate-400">(optional)</span>
            </span>
            <input
              className="input"
              value={author}
              onChange={(e) => setAuthor(e.target.value)}
              placeholder="octocat"
            />
          </label>
          <label className="block">
            <span className="block text-sm font-medium text-slate-700 mb-1">
              Repository <span className="text-slate-400">(optional)</span>
            </span>
            <select
              className="input"
              value={repositoryId}
              onChange={(e) => setRepositoryId(e.target.value)}
            >
              <option value="">Ad-hoc (manual reviews)</option>
              {repositories?.map((repo) => (
                <option key={repo.id} value={repo.id}>
                  {repo.fullName}
                </option>
              ))}
            </select>
          </label>
        </div>

        <label className="block">
          <div className="flex items-center justify-between mb-1">
            <span className="text-sm font-medium text-slate-700">Unified diff</span>
            <button
              type="button"
              className="text-xs text-brand-600 hover:underline"
              onClick={() => {
                setDiff(SAMPLE_DIFF);
                if (!title) setTitle("Add user lookup endpoint");
              }}
            >
              Insert sample
            </button>
          </div>
          <textarea
            className="input font-mono text-xs h-72"
            required
            value={diff}
            onChange={(e) => setDiff(e.target.value)}
            placeholder="diff --git a/file b/file&#10;@@ ... @@&#10;+ added line"
          />
        </label>

        {error && <p className="text-sm text-red-600">{error}</p>}

        <div className="flex gap-3">
          <button type="submit" disabled={submit.isPending} className="btn-primary">
            {submit.isPending ? "Submitting…" : "Run AI review"}
          </button>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => navigate("/reviews")}
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
}
