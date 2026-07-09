export default function Spinner({ label }: { label?: string }) {
  return (
    <div className="flex items-center gap-3 text-slate-500 py-8">
      <span className="h-5 w-5 rounded-full border-2 border-slate-300 border-t-brand-600 animate-spin" />
      {label && <span className="text-sm">{label}</span>}
    </div>
  );
}
