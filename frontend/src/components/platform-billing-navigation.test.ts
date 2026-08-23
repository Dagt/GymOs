import test from "node:test";
import assert from "node:assert/strict";
import { visibleSectionsForConsole } from "./app-shell-model.ts";

test("platform administrator sees billing navigation", () => {
  const sections = visibleSectionsForConsole({
    consoleType: "PLATFORM_OWNER",
    hasBranch: false,
    permissions: new Set(["platform.admin"]),
    variant: "platform",
  });

  const links = sections.flatMap((section) => section.links);
  assert.ok(links.some((link) => link.href === "/platform/gymnasiums"));
  assert.ok(links.some((link) => link.href === "/platform/billing"));
});
