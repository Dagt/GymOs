import type { CurrentUser } from "@/lib/api";

export function getPostLoginPath(user: CurrentUser) {
  const permissions = new Set(user.permissions);
  const username = user.username.toLowerCase();

  if (username.includes("admin") || permissions.has("organization.update") || permissions.has("branch.update")) {
    return user.branchId ? "/dashboard" : "/admin";
  }

  if (permissions.has("report.sales.read") || permissions.has("catalog.manage")) {
    return user.branchId ? "/dashboard" : "/admin";
  }

  return "/reception";
}

