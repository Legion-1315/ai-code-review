import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { useEvalReport } from "../api/hooks";
import Spinner from "../components/Spinner";
import type { EvalEngineScore } from "../api/types";

const pct = (v: number) => `${Math.round(v * 100)}%`;

function EngineCard({ score }: { score: EvalEngineScore }) {
  const isClaude = score.engine === "claude";
  return (
    <div className="bg-white rounded-lg border border-slate-200 p-5">
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-semibold text-slate-800 capitalize">
          {isClaude ? "Claude (real AI)" : "Heuristic engine (fallback)"}
        </h3>
        <span
          className={`text-xs px-2 py-1 rounded-full font-medium ${
            isClaude
              ? "bg-violet-100 text-violet-700"
              : "bg-slate-100 text-slate-600"
          }`}
        >
          {score.engine}
        </span>
      </div>
      <div className="grid grid-cols-3 gap-3 mb-4">
        <Stat label="Precision" value={pct(score.precision)} />
        <Stat label="Recall" value={pct(score.recall)} />
        <Stat label="F1" value={pct(score.f1)} />
      </div>
      <dl className="text-sm space-y-1.5 text-slate-600">
        <Row
          label="Heuristic-detectable bugs"
          value={pct(score.heuristicDetectableRecall)}
        />
        <Row label="Semantic bugs (need real AI)" value={pct(score.semanticRecall)} />
        <Row label="False positives on clean diffs" value={String(score.cleanFalsePositives)} />
        <Row label="Hallucinated line anchors (demoted)" value={String(score.demotedAnchors)} />
      </dl>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="text-center bg-slate-50 rounded-md py-2">
      <div className="text-xl font-bold text-slate-800">{value}</div>
      <div className="text-xs text-slate-500">{label}</div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between">
      <dt>{label}</dt>
      <dd className="font-medium text-slate-800">{value}</dd>
    </div>
  );
}

export default function EvalsPage() {
  const { data: report, isLoading, isError } = useEvalReport();

  if (isLoading) return <Spinner />;
  if (isError || !report) {
    return (
      <p className="text-slate-500">
        No evaluation report is available in this build.
      </p>
    );
  }

  const categoryData = (report.engines[0]?.categories ?? []).map((c) => {
    const row: Record<string, string | number> = {
      category: c.category.replace("_", " "),
    };
    for (const engine of report.engines) {
      const match = engine.categories.find((x) => x.category === c.category);
      const total = (match?.tp ?? 0) + (match?.fn ?? 0);
      row[engine.engine] = total === 0 ? 0 : Math.round(((match?.tp ?? 0) / total) * 100);
    }
    return row;
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Evals</h1>
        <p className="text-slate-500 mt-1">
          Measured reviewer quality on {report.dataset.cases} labeled diffs (
          {report.dataset.labeledBugs} planted bugs, {report.dataset.cleanCases} clean
          controls). Generated {new Date(report.generatedAt).toLocaleString()}.
        </p>
      </div>

      <div className="grid md:grid-cols-2 gap-4">
        {report.engines.map((engine) => (
          <EngineCard key={engine.engine} score={engine} />
        ))}
        {report.engines.length === 1 && (
          <div className="bg-white rounded-lg border border-dashed border-slate-300 p-5 flex items-center justify-center text-center">
            <p className="text-sm text-slate-500">
              The real Claude engine has not been scored in this build.
              <br />
              Run <code className="bg-slate-100 px-1 rounded">mvn test</code> with{" "}
              <code className="bg-slate-100 px-1 rounded">ANTHROPIC_API_KEY</code> set to
              add it to the scoreboard.
            </p>
          </div>
        )}
      </div>

      <div className="bg-white rounded-lg border border-slate-200 p-5">
        <h3 className="font-semibold text-slate-800 mb-4">
          Recall by category (%, labeled bugs found)
        </h3>
        <div className="h-64">
          <ResponsiveContainer>
            <BarChart data={categoryData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="category" tick={{ fontSize: 12 }} />
              <YAxis domain={[0, 100]} tick={{ fontSize: 12 }} />
              <Tooltip />
              <Legend />
              {report.engines.map((engine, i) => (
                <Bar
                  key={engine.engine}
                  dataKey={engine.engine}
                  fill={i === 0 ? "#94a3b8" : "#8b5cf6"}
                  radius={[4, 4, 0, 0]}
                />
              ))}
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="bg-white rounded-lg border border-slate-200 p-5">
        <h3 className="font-semibold text-slate-800 mb-2">Methodology</h3>
        <p className="text-sm text-slate-600 leading-relaxed">{report.methodology}</p>
      </div>
    </div>
  );
}
