import { expect, test, type Page, type Route } from "@playwright/test";

const coachId = "11111111-1111-4111-8111-111111111111";
const workoutId = "22222222-2222-4222-8222-222222222222";
const replacementWorkoutId = "33333333-3333-4333-8333-333333333333";
const versionId = "44444444-4444-4444-8444-444444444444";
const clientId = "55555555-5555-4555-8555-555555555555";
const assignmentId = "66666666-6666-4666-8666-666666666666";
const exerciseId = "77777777-7777-4777-8777-777777777777";
const userId = "88888888-8888-4888-8888-888888888888";
const ownerUserId = "12121212-1212-4212-8212-121212121212";
const branchId = "99999999-9999-4999-8999-999999999999";
const secondBranchId = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";

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
    await expect(page.getByText("Tu perfil se asigna automáticamente.")).toBeVisible();
    await expect(page.getByRole("combobox", { name: "Coach", exact: true })).toHaveCount(0);
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
    await page.getByRole("button", { name: "Editar detalles" }).click();
    await page.getByLabel("Nombre").fill("Press inclinado actualizado");
    await page.getByRole("button", { name: "Actualizar ejercicio" }).click();
    await expect.poll(() => updatePayload?.name).toBe("Press inclinado actualizado");

    await page.getByRole("button", { name: "Editar detalles" }).click();
    await page.getByRole("button", { name: "Press superior ×" }).click();
    await expect.poll(() => aliasDeleted).toBe(true);
    await page.locator('input[type="file"]').setInputFiles({
      name: "press.webp",
      mimeType: "image/webp",
      buffer: Buffer.from([1, 2, 3, 4]),
    });
    await expect.poll(() => mediaUploaded).toBe(true);
  });

  test("cancela con motivo y filtra sus métricas históricas por socio", async ({ page }) => {
    let cancelledWith: Record<string, unknown> | null = null;
    let replacementPayload: Record<string, unknown> | null = null;
    let reportClientFilter = "";
    let trendWeeksFilter = "";
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
        reportClientFilter = url.searchParams.get("clientId") ?? reportClientFilter;
        await route.fulfill({ contentType: "application/json", body: JSON.stringify(report()) });
        return true;
      }
      if (path === "/reports/coaches/workout-assignments/trend") {
        trendWeeksFilter = url.searchParams.get("weeks") ?? trendWeeksFilter;
        reportClientFilter = url.searchParams.get("clientId") ?? reportClientFilter;
        await route.fulfill({ contentType: "application/json", body: JSON.stringify(trend()) });
        return true;
      }
      if (path === "/clients") {
        await route.fulfill({ contentType: "application/json", body: JSON.stringify([client()]) });
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
    await expect(page.getByRole("heading", { name: "Actividad semanal" })).toBeVisible();
    await page.getByPlaceholder("Nombre, correo o teléfono").last().fill("Diego");
    await page.getByRole("button", { name: "Buscar socio" }).click();
    await page.getByRole("button", { name: "Diego García" }).click();
    await expect(page.getByRole("combobox", { name: "Coach", exact: true })).toHaveCount(0);
    await expect(page.getByText("Coaches activos")).toHaveCount(0);
    await expect(page.getByRole("heading", { name: "Desempeño por coach" })).toHaveCount(0);
    await expect(page.getByText("Primeras rutinas", { exact: true }).first()).toBeVisible();
    await page.getByLabel("Histórico").selectOption("26");
    await page.getByRole("button", { name: "Actualizar" }).click();
    await expect.poll(() => reportClientFilter).toBe(clientId);
    await expect.poll(() => trendWeeksFilter).toBe("26");
    await expect(page.getByLabel("Rutinas asignadas por semana")).toBeVisible();
  });

  test("el dueño entra directamente a métricas sin pestañas ni superficies operativas", async ({ page }) => {
    const operationalRequests: string[] = [];
    await bootCoaching(page, async (route, path) => {
      if (path === "/workouts" || path === "/exercises" || path === "/coaches/profiles") {
        operationalRequests.push(path);
      }
      return false;
    }, ownerUser());

    await page.goto("/coaches");

    await expect(page.getByRole("button", { name: "Métricas" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Constructor" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Ejercicios" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Asignaciones" })).toHaveCount(0);
    await expect(page.getByRole("heading", { name: "Información de la rutina" })).toHaveCount(0);
    await expect(page.getByRole("heading", { name: "Actividad semanal" })).toBeVisible();
    await expect(page.getByRole("button", { name: "PDF" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Cancelar", exact: true })).toHaveCount(0);
    expect(operationalRequests).toEqual([]);
  });

  test("el gerente entra directamente a métricas aunque conserve un token operativo anterior", async ({ page }) => {
    const operationalRequests: string[] = [];
    await bootCoaching(page, async (route, path) => {
      if (path === "/workouts" || path === "/exercises" || path === "/coaches/profiles") {
        operationalRequests.push(path);
      }
      return false;
    }, managerWithStalePermissions());

    await page.goto("/coaches");

    await expect(page.getByRole("button", { name: "Métricas" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Constructor" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Ejercicios" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Asignaciones" })).toHaveCount(0);
    await expect(page.getByRole("heading", { name: "Información de la rutina" })).toHaveCount(0);
    await expect(page.getByRole("heading", { name: "Actividad semanal" })).toBeVisible();
    await expect(page.getByRole("button", { name: "PDF" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Cancelar", exact: true })).toHaveCount(0);
    expect(operationalRequests).toEqual([]);
  });

  test("administra el perfil del coach desde Equipo y accesos", async ({ page }) => {
    let profilePayload: Record<string, unknown> | null = null;
    await bootCoaching(page, async (route, path) => {
      if (route.request().method() === "PUT" && path === `/coaches/profiles/${userId}`) {
        profilePayload = route.request().postDataJSON() as Record<string, unknown>;
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify({
            ...coachProfile(),
            specialties: ["Fuerza", "Movilidad"],
            bio: "Entrenamiento funcional y fuerza.",
            branches: [
              { id: branchId, name: "Centro" },
              { id: secondBranchId, name: "Norte" },
            ],
          }),
        });
        return true;
      }
      return false;
    }, ownerUser());

    await page.goto("/users");
    await expect(page.getByRole("heading", { name: "Equipo del gimnasio" })).toBeVisible();
    await page.getByRole("button", { name: "Datos de Coach" }).click();
    await page.getByLabel("Especialidades").fill("Fuerza, Movilidad");
    await page.getByLabel("Biografía").fill("Entrenamiento funcional y fuerza.");
    await page.getByRole("checkbox", { name: "Norte", exact: true }).check();
    await page.getByRole("button", { name: "Guardar perfil" }).click();

    await expect.poll(() => profilePayload).toMatchObject({
      specialties: ["Fuerza", "Movilidad"],
      bio: "Entrenamiento funcional y fuerza.",
      status: "ACTIVE",
      branchIds: [branchId, secondBranchId],
    });
    await expect(page.getByText("Perfil y sucursales del coach actualizados.")).toBeVisible();
  });

  test("mantiene alineadas las acciones de Coach con el resto del equipo", async ({ page }) => {
    await bootCoaching(page, async (route, path) => {
      if (route.request().method() === "GET" && path === "/users/internal") {
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify([
            internalCoach(),
            {
              ...internalCoach(),
              id: "cdcdcdcd-cdcd-4dcd-8dcd-cdcdcdcdcdcd",
              fullName: "Usuario Recepción",
              username: "recepcion@gymos.test",
              roles: ["Recepcion"],
            },
          ]),
        });
        return true;
      }
      return false;
    }, ownerUser());

    await page.goto("/users");
    const coachRow = page.locator(".team-user-table article").filter({ hasText: "Coach Uno" });
    const receptionRow = page.locator(".team-user-table article").filter({ hasText: "Usuario Recepción" });
    const profileButton = coachRow.getByRole("button", { name: "Datos de Coach" });
    const suspendButton = coachRow.getByRole("button", { name: "Suspender" });
    await expect(profileButton).toBeVisible();
    await expect(suspendButton).toBeVisible();

    const [profileBox, suspendBox, coachBox, receptionBox] = await Promise.all([
      profileButton.boundingBox(),
      suspendButton.boundingBox(),
      coachRow.boundingBox(),
      receptionRow.boundingBox(),
    ]);
    expect(profileBox).not.toBeNull();
    expect(suspendBox).not.toBeNull();
    expect(coachBox).not.toBeNull();
    expect(receptionBox).not.toBeNull();
    expect(Math.abs((profileBox?.y ?? 0) - (suspendBox?.y ?? 0))).toBeLessThanOrEqual(2);
    expect(Math.abs((coachBox?.height ?? 0) - (receptionBox?.height ?? 0))).toBeLessThanOrEqual(2);
  });

  test("crea la cuenta y los datos adicionales del Coach desde una sola alta", async ({ page }) => {
    let createPayload: Record<string, unknown> | null = null;
    await bootCoaching(page, async (route, path) => {
      if (route.request().method() === "POST" && path === "/users/internal") {
        createPayload = route.request().postDataJSON() as Record<string, unknown>;
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify({
            ...internalCoach(),
            id: "abababab-abab-4bab-8bab-abababababab",
            fullName: "Ana Coach",
            username: "ana.coach",
            email: "ana@example.com",
            branches: ["Norte"],
            branchIds: [secondBranchId],
          }),
        });
        return true;
      }
      return false;
    }, ownerUser());

    await page.goto("/users");
    await page.getByRole("button", { name: "Crear usuario" }).first().click();
    await page.getByLabel("Nombre").fill("Ana");
    await page.getByLabel("Apellido paterno").fill("Coach");
    await page.getByLabel("Email").fill("ana@example.com");
    await page.getByLabel("Telefono").fill("5512345678");
    await page.getByLabel("Usuario de inicio de sesion").fill("ana.coach");
    await page.getByLabel("Contrasena temporal").fill("Temporal123!");
    await expect(page.getByText("Información del Coach")).toBeVisible();
    await page.getByLabel("Especialidades").fill("Fuerza, Movilidad");
    await page.getByLabel("Biografía").fill("Entrenadora funcional.");
    await page.getByRole("checkbox", { name: "Norte", exact: true }).check();
    await page.getByRole("button", { name: "Crear usuario" }).last().click();

    await expect.poll(() => createPayload).toMatchObject({
      firstName: "Ana",
      lastName: "Coach",
      username: "ana.coach",
      roleId: "13131313-1313-4313-8313-131313131313",
      branchIds: [secondBranchId],
      coachSpecialties: ["Fuerza", "Movilidad"],
      coachBio: "Entrenadora funcional.",
    });
    await expect(page.getByText("Usuario y perfil de Coach creados.")).toBeVisible();
  });
});

async function bootCoaching(
  page: Page,
  override?: (route: Route, path: string, url: URL) => Promise<boolean>,
  user = coachUser(),
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
  }, { user });
}

function responseFor(path: string) {
  if (path === "/branches" || path === "/organizations/current/branches") {
    return [
      { id: branchId, name: "Centro" },
      { id: secondBranchId, name: "Norte" },
    ];
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
  if (path === "/coaches/profiles") return [coachProfile()];
  if (path === "/organizations/current") {
    return { id: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", tradeName: "GymOS Test" };
  }
  if (path === "/users/roles") {
    return [{
      id: "13131313-1313-4313-8313-131313131313",
      name: "Coach",
      description: "Entrenamiento",
      systemRole: true,
      permissionCount: 8,
    }];
  }
  if (path === "/users/internal") return [internalCoach()];
  if (path === "/workouts") return workouts();
  if (path === "/exercises") {
    return { items: [exercise()], page: 0, size: 100, totalElements: 1, totalPages: 1 };
  }
  if (path === "/reports/coaches/workout-assignments/summary") return report();
  if (path === "/reports/coaches/workout-assignments/trend") return trend();
  return [];
}

function coachProfile() {
  return {
    userId,
    displayName: "Coach Uno",
    email: "coach@gymos.test",
    phone: "555-0100",
    specialties: ["Fuerza"],
    bio: "Entrenamiento de fuerza.",
    status: "ACTIVE",
    branches: [{ id: branchId, name: "Centro" }],
  };
}

function internalCoach() {
  return {
    id: userId,
    fullName: "Coach Uno",
    username: "coach@gymos.test",
    email: "coach@gymos.test",
    phone: "555-0100",
    status: "ACTIVE",
    roles: ["Coach"],
    branches: ["Centro"],
    branchIds: [branchId],
  };
}

function client() {
  return {
    id: clientId,
    firstName: "Diego",
    lastName: "García",
    email: "diego@example.com",
    phone: "555-0200",
    status: "ACTIVE",
  };
}

function trend() {
  return {
    from: "2026-05-04",
    to: "2026-07-26",
    weeks: 12,
    points: Array.from({ length: 12 }, (_, index) => ({
      weekFrom: `2026-${String(5 + Math.floor(index / 4)).padStart(2, "0")}-${String(4 + ((index % 4) * 7)).padStart(2, "0")}`,
      weekTo: "2026-07-26",
      assignments: index % 4,
      uniqueClients: index % 3,
      firstWorkouts: index % 2,
    })),
  };
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

function coachUser() {
  return {
    userId,
    organizationId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
    branchId,
    username: "coach@gymos.test",
    fullName: "Coach Uno",
    permissions: [
      "coach.read", "exercise.read", "exercise.create",
      "workout.read.own", "workout.manage.own", "workout.assign",
      "workout.pdf.download", "coach.metrics.read",
    ],
    roles: ["Coach"],
    platformAdmin: false,
    supportMode: false,
    console: "COACH",
    organizationName: "GymOS Test",
    brandName: "GymOS Test",
    logoUrl: "",
    primaryColor: "#173c2a",
    accentColor: "#EAB308",
    surfaceColor: "#F4F7FB",
  };
}

function ownerUser() {
  return {
    ...coachUser(),
    userId: ownerUserId,
    username: "owner@gymos.test",
    fullName: "Owner GymOS",
    permissions: [
      "branch.read",
      "client.read",
      "coach.read",
      "coach.metrics.read",
      "coach.profile.manage",
      "user.read",
      "user.manage",
    ],
    roles: ["Dueno del gimnasio"],
    console: "TENANT_OWNER",
  };
}

function managerWithStalePermissions() {
  return {
    ...coachUser(),
    userId: ownerUserId,
    username: "manager@gymos.test",
    fullName: "Manager GymOS",
    permissions: [
      "branch.read",
      "client.read",
      "coach.read",
      "coach.metrics.read",
      "exercise.read",
      "exercise.manage",
      "workout.read.all",
      "workout.manage.all",
      "workout.assign",
      "workout.pdf.download",
    ],
    roles: ["Gerente"],
    console: "TENANT_MANAGER",
  };
}
