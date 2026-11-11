import assert from "node:assert/strict";
import test from "node:test";
import { clearSession, getStoredSession, storeSession, storeCurrentUser } from "./api/session.ts";
import type { CurrentUser } from "./api/auth-types.ts";

const user: CurrentUser = { userId: "qa-user", accountScope: "TENANT", organizationId: "qa-a", branchId: "qa-branch", username: "qa@example.test", fullName: "Prueba", permissions: [], roles: [], platformAdmin: false, supportMode: false, supportExpiresAt: null, console: "TENANT_OWNER", primaryColor: "#173C2A" };
const auth = (current: CurrentUser, token = "qa-token") => ({ accessToken: token, user: current, tokenType: "Bearer" as const, expiresIn: 900 });

function setup() {
  const events = new EventTarget();
  const storage = new Map<string, string>();
  Object.assign(globalThis, { window: Object.assign(events, { sessionStorage: { removeItem: (key: string) => storage.delete(key) }, localStorage: { removeItem: (key: string) => storage.delete(key) } }) });
  clearSession();
  let notifications = 0;
  events.addEventListener("gymos.session-refreshed", () => notifications++);
  return () => notifications;
}

test("login, refresh, branch, tenant and support changes publish the same existing event", () => {
  const notifications = setup();
  for (const next of [user, { ...user, branchId: "qa-other" }, { ...user, organizationId: "qa-b" }, { ...user, accountScope: "PLATFORM" as const, platformAdmin: true, supportMode: true }, { ...user, accountScope: "PLATFORM" as const, organizationId: null, branchId: null, platformAdmin: true, supportMode: false }]) storeSession(auth(next));
  assert.equal(notifications(), 5);
});

test("profile/color updates notify once and logout clears the active context", () => {
  const notifications = setup();
  storeSession(auth(user));
  const expected = getStoredSession()!;
  const changed = { ...user, primaryColor: "#123456", logoUrl: "/qa-logo" };
  assert.equal(storeCurrentUser(changed, expected), true);
  assert.equal(getStoredSession()!.user.primaryColor, "#123456");
  assert.equal(notifications(), 2);
  assert.equal(storeCurrentUser(changed), true);
  assert.equal(notifications(), 2);
  clearSession();
  assert.equal(getStoredSession(), null);
  assert.equal(notifications(), 3);
  assert.equal(storeCurrentUser(user), false);
  assert.equal(notifications(), 3);
});

for (const [name, next] of Object.entries({
  tenant: { ...user, organizationId: "qa-b" }, branch: { ...user, branchId: "qa-other" }, login: { ...user, userId: "qa-other" }, support: { ...user, supportMode: true }, platform: { ...user, accountScope: "PLATFORM" as const },
})) test(`a stale profile cannot overwrite the new ${name} context`, async () => {
  const notifications = setup();
  storeSession(auth(user));
  const old = getStoredSession()!;
  let resolve!: (value: CurrentUser) => void;
  const response = new Promise<CurrentUser>(done => { resolve = done; }).then(profile => storeCurrentUser(profile, old));
  storeSession(auth(next, "qa-next-token"));
  resolve({ ...user, primaryColor: "#AABBCC" });
  assert.equal(await response, false);
  assert.equal(getStoredSession()!.user, next);
  assert.equal(notifications(), 2);
  assert.equal(storeCurrentUser(user), false);
});

test("a delayed profile cannot overwrite more recent colors or a renewed token", () => {
  setup(); storeSession(auth(user));
  const old = getStoredSession()!;
  storeCurrentUser({ ...user, primaryColor: "#654321" });
  assert.equal(storeCurrentUser({ ...user, primaryColor: "#AABBCC" }, old), false);
  assert.equal(getStoredSession()!.user.primaryColor, "#654321");
  const previous = getStoredSession()!;
  storeSession(auth(previous.user, "qa-renewed"));
  assert.equal(storeCurrentUser(user, previous), false);
  assert.equal(getStoredSession()!.accessToken, "qa-renewed");
});
