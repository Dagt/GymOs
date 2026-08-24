import test from "node:test";
import assert from "node:assert/strict";
import { isActive, visibleSectionsForConsole } from "./app-shell-model.ts";

test("platform administrator sees the complete customer management navigation", () => {
  const sections = visibleSectionsForConsole({
    consoleType: "PLATFORM_OWNER",
    hasBranch: false,
    permissions: new Set(["platform.admin"]),
    variant: "platform",
  });

  const links = sections.flatMap((section) => section.links);
  assert.deepEqual(sections.map((section) => section.title), ["Dirección GymFort", "Clientes", "Ingresos"]);
  assert.ok(links.some((link) => link.href === "/platform" && link.label === "Resumen de cartera"));
  assert.ok(links.some((link) => link.href === "/platform/gymnasiums"));
  assert.ok(links.some((link) => link.href === "/platform/billing"));
});

test("portfolio navigation is active only on the platform home", () => {
  assert.equal(isActive("/platform", "/platform"), true);
  assert.equal(isActive("/platform/billing", "/platform"), false);
  assert.equal(isActive("/platform/gymnasiums", "/platform"), false);
  assert.equal(isActive("/platform/gymnasiums", "/platform/gymnasiums"), true);
});
