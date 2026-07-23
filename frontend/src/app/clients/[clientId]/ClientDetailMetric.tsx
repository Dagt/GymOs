export function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="font-medium text-[#6f7a73]">{label}</dt>
      <dd className="mt-1 font-semibold">{value}</dd>
    </div>
  );
}
