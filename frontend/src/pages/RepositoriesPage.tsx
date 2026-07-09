import { useState, type FormEvent } from "react";
import {
  useCreateRepository,
  useDeleteRepository,
  useRepositories,
} from "../api/hooks";
import { errorMessage } from "../api/client";
import Spinner from "../components/Spinner";

export default function RepositoriesPage() {
  const { data, isLoading } = useRepositories();
  const createRepo = useCreateRepository();
  const deleteRepo = useDeleteRepository();

  const [fullName, setFullName] = useState("");
  const [threshold, setThreshold] = useState("60");
  const [error, setError] = useState<string | null>(null);

  const handleCreate = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await createRepo.mutateAsync({
        fullName,
        minScoreThreshold: Number(threshold),
      });
      setFullName("");
      setThreshold("60");
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  return (
    <div className="space-y-6 max-w-3xl">
      <header>
        <h1 className="text-2xl font-bold">Repositories</h1>
        <p className="text-slate-500 text-sm">
          Connect a repository so GitHub webhook deliveries are auto-reviewed.
        </p>
      </header>

      <form onSubmit={handleCreate} className="card space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <label className="block sm:col-span-2">
            <span className="block text-sm font-medium text-slate-700 mb-1">
              Repository full name
            </span>
            <input
              className="input"
              required
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="octocat/hello-world"
            />
          </label>
          <label className="block">
            <span className="block text-sm font-medium text-slate-700 mb-1">
              Min score
            </span>
            <input
              className="input"
              type="number"
              min={0}
              max={100}
              value={threshold}
              onChange={(e) => setThreshold(e.target.value)}
            />
          </label>
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button type="submit" disabled={createRepo.isPending} className="btn-primary">
          {createRepo.isPending ? "Connecting…" : "Connect repository"}
        </button>
      </form>

      {isLoading ? (
        <Spinner label="Loading repositories…" />
      ) : data && data.length > 0 ? (
        <div className="card p-0 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-slate-500 text-left">
              <tr>
                <th className="px-4 py-3 font-medium">Repository</th>
                <th className="px-4 py-3 font-medium">Min score</th>
                <th className="px-4 py-3 font-medium"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.map((repo) => (
                <tr key={repo.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 font-medium">{repo.fullName}</td>
                  <td className="px-4 py-3 text-slate-600">{repo.minScoreThreshold}</td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => deleteRepo.mutate(repo.id)}
                      className="text-sm text-red-600 hover:underline"
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="card text-center text-slate-500 py-10">
          No repositories connected yet.
        </div>
      )}
    </div>
  );
}
