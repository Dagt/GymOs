import Link from "next/link";
import { getQuickActions, getFocusActions } from "./reception-model";

export function ReceptionHubGrid() {
  const actions = getFocusActions();

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      {actions.map((action, index) => {
        // Highlight the first action (POS)
        const isPrimary = index === 0;
        return (
          <Link
            key={action.href}
            href={action.href}
            className={`flex flex-col p-6 rounded-xl border transition-all hover:shadow-md ${
              isPrimary 
                ? "border-[color:var(--brand-primary)] bg-[color-mix(in_srgb,var(--brand-primary)_5%,white)] shadow-sm" 
                : "border-gray-200 bg-white hover:border-gray-300"
            }`}
          >
            <div className="flex items-center justify-between mb-4">
              <span className={`text-sm font-bold px-3 py-1 rounded-full ${
                isPrimary 
                  ? "bg-[color:var(--brand-primary)] text-white" 
                  : "bg-gray-100 text-gray-700"
              }`}>
                {action.title}
              </span>
            </div>
            <h3 className={`text-xl font-bold mb-2 ${isPrimary ? "text-[color:var(--brand-primary)]" : "text-gray-900"}`}>
              {action.label}
            </h3>
            <p className="text-gray-600 text-sm flex-grow">
              {action.detail}
            </p>
          </Link>
        );
      })}
    </div>
  );
}
