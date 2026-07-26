import { expect, test, type Page, type Route } from "@playwright/test";

const coachId = "11111111-1111-4111-8111-111111111111";
const workoutId = "22222222-2222-4222-8222-222222222222";
const replacementWorkoutId = "33333333-3333-4333-8333-333333333333";
const versionId = "44444444-4444-4444-8444-444444444444";
const clientId = "55555555-5555-4555-8555-555555555555";
const assignmentId = "66666666-6666-4666-8666-666666666666";
const exerciseId = "77777777-7777-4777-8777-777777777777";
const userId = "88888888-8888-4888-8888-888888888888";

test.describe("coaches y rutinas", () => {
  test("construye bloques estructurados y publica la rutina", async ({ page }) => {
    let createdPayload: Record<string, unknown> | null = null;
    await bootCoaching(page, async (route, path) => {
      if (route.request().method() === "POST" && path === "/workouts") {
        createdPayload = route.request().postDataJSON() as Record<string, unknown>;
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify(workoutDetail(workoutId, "Rutina E2E", "DRAFT")),
        });
        return true;
      }
      if (route.request().method() === "POST" && path === `/workouts/${workoutId}/publish`) {
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify(workoutDetail(workoutId, "Rutina E2E", "ACTIVE")),
        });
        return true;
      }
      return false;
    });

    await page.goto("/coaches");
    await expect(page.getByRole("heading", { name: "Coaches y rutinas" })).toBeVisible();
    await page.getByLabel("Título").fill("Rutina E2E");
    await page.getByRole("combobox", { name: "Ejercicio", exact: true }).fill("Sentadilla libre");
    await page.getByLabel("Series").fill("4");
    await page.getByLabel("Repeticiones").fill("8-10");
    await page.getByRole("button", { name: "+ Agregar bloque" }).click();
    await page.getByRole("combobox", { name: "Ejercicio", exact: true }).nth(1).fill("Peso muerto");
    await page.getByRole("button", { name: "Publicar rutina" }).click();

    await expect(page.getByText("Rutina “Rutina E2E” publicada correctamente.")).toBeVisible();
    const content = JSON.parse(String(createdPayload?.contentJson)) as {
      sessions: Array<{ blocks: Array<{ exercises: Array<Record<string, unknown>> }> }>;
    };
    expect(content.sessions[0].blocks).toHaveLength(2);
    expect(content.sessions[0].blocks[0].exercises[0]).toMatchObject({
      exerciseName: "Sentadilla libre",
      sets: 4,
      reps: "8-10",
    });
  });

  test("edita ejercicio propio, gestiona alias y carga un medio", async ({ page }) => {
    let updatePayload: Record<string, unknown> | null = null;
    let aliasDeleted = false;
    let mediaUploaded = false;
    await bootCoaching(page, async (route, path) => {
      if (route.request().method() === "GET" && path === `/exercises/${exerciseId}/aliases`) {
        await route.fulfill({ contentType: "application/json", body: JSON.stringify(["Press superior"]) });
        return true;
      }
      if (route.request().method() === "PUT" && path === `/exercises/${exerciseId}`) {
        updatePayload = route.request().postDataJSON() as Record<string, unknown>;
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify({ ...exercise(), name: updatePayload.name }),
        });
        return true;
      }
      if (route.request().method() === "DELETE" && path === `/exercises/${exerciseId}/aliases`) {
        aliasDeleted = true;
        await route.fulfill({ contentType: "application/json", body: "[]" });
        return true;
      }
      if (route.request().method() === "POST" && path === `/exercises/${exerciseId}/media`) {
        mediaUploaded = true;
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify({ ...exercise(), mediaUrl: "coaching/media.webp" }),
        });
        return true;
      }
      return false;
    });

    await page.goto("/coaches");
    await page.getByRole("button", { name: "Ejercicios" }).click();
    await page.getByRole("button", { name: "Editar y ver aliases" }).click();
    await page.getByLabel("Nombre").fill("Press inclinado actualizado");
    await page.getByRole("button", { name: "Actualizar ejercicio" }).click();
    await expect.poll(() => updatePayload?.name).toBe("Press inclinado actualizado");

    await page.getByRole("button", { name: "Editar y ver aliases" }).click();
    await page.getByRole("button", { name: "Press superior ×" }).click();
    await expect.poll(() => aliasDeleted).toBe(true);
    await page.locator('input[type="file"]').setInputFiles({
      name: "press.webp",
      mimeType: "image/webp",
      buffer: Buffer.from([1, 2, 3, 4]),
    });
    await expect.poll(() => mediaUploaded).toBe(true);
  });

  test("cancela con motivo, reemplaza y filtra métricas por coach", async ({ page }) => {
    let cancelledWith: Record<string, unknown> | null = null;
    let replacementPayload: Record<string, unknown> | null = null;
    let reportCoachFilter = "";
    await bootCoaching(page, async (route, path, url) => {
      if (route.request().method() === "PATCH" && path === `/workout-assignments/${assignmentId}/status`) {
        cancelledWith = route.request().postDataJSON() as Record<string, unknown>;
        await route.fulfill({ contentType: "application/json", body: JSON.stringify({ id: assignmentId }) });
        return true;
      }
      if (route.request().method() === "POST" && path === `/workout-assignments/${assignmentId}/replace`) {
        replacementPayload = route.request().postDataJSON() as Record<string, unknown>;
        await route.fulfill({ contentType: "application/json", body: JSON.stringify({ id: "replacement" }) });
        return true;
      }
      if (path === "/reports/coaches/workout-assignments/summary") {
        reportCoachFilter = url.searchParams.get("coachId") ?? reportCoachFilter;
        await route.fulfill({ contentType: "application/json", body: JSON.stringify(report()) });
        return true;
      }
      return false;
    });

    await page.goto("/coaches");
    await page.getByRole("button", { name: "Asignaciones" }).click();
    await page.getByRole("button", { name: "Cancelar", exact: true }).click();
    await page.getByPlaceholder("Motivo obligatorio de cancelación").fill("Cambio de objetivo");
    await page.getByRole("button", { name: "Confirmar cancelación" }).click();
    await expect.poll(() => cancelledWith).toMatchObject({
      status: "CANCELLED",
      reason: "Cambio de objetivo",
    });

    await page.getByRole("button", { name: "Reemplazar", exact: true }).click();
    await page.getByLabel("Selecciona la nueva rutina").selectOption(replacementWorkoutId);
    await page.getByRole("button", { name: "Confirmar reemplazo" }).click();
    await expect.poll(() => replacementPayload).toMatchObject({
      clientId,
      workoutId: replacementWorkoutId,
    });

    await page.getByRole("button", { name: "Métricas" }).click();
    await expect(page.getByText("+2 vs. anterior")).toBeVisible();
    await page.getByLabel("Coach").last().selectOption(coachId);
    await page.getByRole("button", { name: "Actualizar" }).click();
    await expect.poll(() => reportCoachFilter).toBe(coachId);
  });
});

async function bootCoaching(
  page: Page,
  override?: (route: Route, path: string, url: URL) => Promise<boolean>,
) {
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/^\/api\/v1/, "");
    if (override && await override(route, path, url)) return;
    await route.fulfill({ contentType: "application/json", body: JSON.stringify(responseFor(path)) });
  });
  await page.addInitScript(({ user }) => {
    window.localStorage.setItem("gymos.accessToken", "coaching-e2e-access");
    window.localStorage.setItem("gymos.refreshToken", "coaching-e2e-refresh");
    window.localStorage.setItem("gymos.currentUser", JSON.stringify(user));
  }, { user: currentUser() });
}

function responseFor(path: string) {
  if (path === "/branches" || path === "/organizations/current/branches") {
    return [{ id: "99999999-9999-4999-8999-999999999999", name: "Centro" }];
  }
  if (path === "/coaches") {
    return [{
      id: coachId,
      userAccountId: userId,
      displayName: "Coach Uno",
      email: null,
      phone: null,
      specialtiesJson: "[]",
      bio: null,
      status: "ACTIVE",
      createdAt: "2026-07-01T00:00:00Z",
      updatedAt: "2026-07-01T00:00:00Z",
    }];
  }
  if (path === "/workouts") return workouts();
  if (path === "/exercises") {
    return { items: [exercise()], page: 0, size: 100, totalElements: 1, totalPages: 1 };
  }
  if (path === "/reports/coaches/workout-assignments/summary") return report();
  return [];
}

function workouts() {
  return [
    workout(workoutId, "Fuerza base"),
    workout(replacementWorkoutId, "Hipertrofia"),
  ];
}

function workout(id: string, title: string) {
  return {
    id,
    coachId,
    coachName: "Coach Uno",
    title,
    goal: "Fuerza",
    level: "INTERMEDIATE",
    status: "ACTIVE",
    latestVersionId: versionId,
    latestVersionNumber: 2,
    createdAt: "2026-07-01T00:00:00Z",
    updatedAt: "2026-07-01T00:00:00Z",
  };
}

function workoutDetail(id: string, title: string, status: string) {
  return {
    workout: { ...workout(id, title), status },
    versions: [],
  };
}

function exercise() {
  return {
    id: exerciseId,
    organizationId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
    name: "Press inclinado",
    description: "Empuje inclinado",
    instructions: "Controlar el descenso",
    primaryMuscleGroup: "Pecho",
    secondaryMuscleGroups: "Tríceps",
    movementPattern: "Empuje",
    type: "STRENGTH",
    equipment: "Mancuerna",
    suggestedLevel: "INTERMEDIATE",
    laterality: "BILATERAL",
    mediaUrl: null,
    warnings: null,
    origin: "COACH",
    status: "ACTIVE",
    createdBy: userId,
  };
}

function report() {
  return {
    from: "2026-07-20",
    to: "2026-07-26",
    assignments: 3,
    uniqueClients: 2,
    activeCoaches: 1,
    coachesWithoutAssignments: 0,
    previousFrom: "2026-07-13",
    previousTo: "2026-07-19",
    previousAssignments: 1,
    previousUniqueClients: 1,
    assignmentChange: 2,
    uniqueClientChange: 1,
    page: 0,
    size: 25,
    totalDetails: 1,
    totalPages: 1,
    coaches: [{
      coachId,
      coachName: "Coach Uno",
      assignments: 3,
      uniqueClients: 2,
      firstWorkouts: 1,
    }],
    details: [{
      id: assignmentId,
      assignedOn: "2026-07-25",
      status: "ACTIVE",
      statusReason: null,
      replacedAssignmentId: null,
      startsOn: "2026-07-28",
      endsOn: null,
      workoutId,
      workoutTitle: "Fuerza base",
      versionNumber: 2,
      clientId,
      clientName: "Diego García",
      coachId,
      coachName: "Coach Uno",
      printableNotes: "Cuidar técnica",
      privateNotes: null,
    }],
  };
}

function currentUser() {
  return {
    userId,
    organizationId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
    branchId: "99999999-9999-4999-8999-999999999999",
    username: "owner@gymos.test",
    fullName: "Owner GymOS",
    permissions: [
      "coach.read", "exercise.read", "exercise.create", "exercise.manage",
      "workout.read.all", "workout.manage.all", "workout.assign",
      "workout.pdf.download", "coach.metrics.read",
    ],
    roles: ["Dueno del gimnasio"],
    platformAdmin: false,
    supportMode: false,
    console: "TENANT_OWNER",
    organizationName: "GymOS Test",
    brandName: "GymOS Test",
    logoUrl: "",
    primaryColor: "#173c2a",
    accentColor: "#EAB308",
    surfaceColor: "#F4F7FB",
  };
}
