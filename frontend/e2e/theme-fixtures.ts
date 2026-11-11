import type { Page, Route } from "@playwright/test";
import type { CurrentUser } from "../src/lib/api/auth-types";
import { stock, paged } from "./pagination-fixtures";

export const themeUser: CurrentUser = {
  userId: "33333333-3333-4333-8333-333333333333", accountScope: "TENANT", organizationId: "11111111-1111-4111-8111-111111111111", branchId: "22222222-2222-4222-8222-222222222222",
  username: "theme@example.test", fullName: "Tema Prueba", permissions: ["organization.read", "organization.update", "branch.read", "inventory.read", "inventory.manage", "catalog.read", "sale.read", "sale.create"], roles: ["Dueno del gimnasio"], platformAdmin: false, supportMode: false, supportExpiresAt: null, console: "TENANT_OWNER", organizationName: "Gimnasio Prueba", brandName: "Paleta Prueba", logoUrl: "", primaryColor: "#673A82", accentColor: "#F5AE31", surfaceColor: "#F8F3FC",
};

export async function bootTheme(page: Page, initial: CurrentUser = themeUser, authenticated = true) {
  const state = { user: { ...initial }, authenticated, refreshes: 0, profiles: 0, delay: 0,
    profileHandler: null as ((route: Route) => Promise<void>) | null, expireInventory: false };
  await page.addInitScript(() => sessionStorage.setItem("gymfort.session.slot", "00000000-0000-4000-8000-000000000001"));
  const auth = () => ({ user: state.user, accessToken: `eyJhbGciOiJub25lIn0.${Buffer.from(JSON.stringify({ sid: "theme-qa", ctx: 1, exp: Math.floor(Date.now() / 1000) + 900 })).toString("base64url")}.qa`, tokenType: "Bearer", expiresIn: 900 });
  const organization = () => ({ id: state.user.organizationId, legalName: "Gimnasio Prueba", tradeName: state.user.organizationName, status: "ACTIVE", timezone: "America/Mexico_City", defaultCurrency: "MXN", taxId: null, contactEmail: null, contactPhone: null, brandName: state.user.brandName, logoUrl: state.user.logoUrl, primaryColor: state.user.primaryColor, accentColor: state.user.accentColor, surfaceColor: state.user.surfaceColor });
  await page.route("**/api/v1/**", async route => {
    const url = new URL(route.request().url()); const path = url.pathname.replace("/api/v1", "");
    const send = (value: unknown, status = 200) => route.fulfill({ status, json: value });
    if (path === "/auth/refresh") { state.refreshes++; if (state.delay) await new Promise(r => setTimeout(r, state.delay)); return send(state.authenticated ? auth() : {}, state.authenticated ? 200 : 401); }
    if (path === "/auth/login") { state.authenticated = true; return send(auth()); }
    if (path === "/auth/logout") { state.authenticated = false; return route.fulfill({ status: 204 }); }
    if (path === "/auth/me") { state.profiles++; return state.profileHandler ? state.profileHandler(route) : send(state.user); }
    if (path === "/auth/select-branch") { state.user = { ...state.user, branchId: route.request().postDataJSON().branchId }; return send(auth()); }
    if (path === "/auth/exit-support") { state.user = { ...state.user, accountScope: "PLATFORM", platformAdmin: true, supportMode: false, supportExpiresAt: null, organizationId: null, branchId: null, console: "PLATFORM_OWNER" }; return send(auth()); }
    if (path === "/platform/customers") return send({ customers: [], summary: { customerCount: 0, activeCustomerCount: 0, onboardingCustomerCount: 0, atRiskCustomerCount: 0, suspendedCustomerCount: 0, overdueBranchCount: 0, followUpsDueCount: 0, monthlyRecurringRevenue: 0 } });
    if (path.startsWith("/reports")) return send({ message: "Reporte fuera del alcance de esta prueba" }, 503);
    if (path === "/system/info") return send({ capabilities: ["operational-pagination-v1"] });
    if (path === "/organizations/current") {
      if (route.request().method() === "PUT") { state.user = { ...state.user, ...route.request().postDataJSON() }; }
      return send(organization());
    }
    if (path.includes("branches") || path === "/branches") return send([{ id: "22222222-2222-4222-8222-222222222222", name: "Centro", status: "ACTIVE", code: "CENTRO", timezone: "America/Mexico_City", accessScope: "LOCAL" }, { id: "44444444-4444-4444-8444-444444444444", name: "Norte", status: "ACTIVE", code: "NORTE", timezone: "America/Mexico_City", accessScope: "LOCAL" }]);
    if (path === "/inventory/summary") {
      if (state.expireInventory) { state.expireInventory = false; return send({}, 401); }
      return send({ totalValuationCents: 254000, totalTrackedProducts: 127, lowStockCount: 127, outOfStockCount: 0, allowNegativeStock: false });
    }
    if (path === "/inventory/branch-settings") return send({ branchId: state.user.branchId, allowNegativeStock: false });
    if (path === "/inventory/search") return send(paged(stock, url));
    if (path.endsWith("/search")) return send(paged([], url));
    return send([]);
  });
  return state;
}
