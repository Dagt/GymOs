import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const pagePath = path.resolve(process.cwd(), "src/app/coaches/page.tsx");
let source = fs.readFileSync(pagePath, "utf8");
const marker = "// Native coaches UX v1";

if (source.includes(marker)) {
  process.exit(0);
}

function replaceOnce(oldText, newText, label) {
  if (!source.includes(oldText)) {
    throw new Error(`No se encontró el bloque requerido para ${label}.`);
  }
  source = source.replace(oldText, newText);
}

replaceOnce(
  'type Tab = "builder" | "catalog" | "assignments" | "metrics";\n',
  'type Tab = "builder" | "catalog" | "assignments" | "metrics";\n\n// Native coaches UX v1\n',
  "marcador de implementación",
);

replaceOnce(
`  function addCatalogExercise(sessionIndex: number, blockIndex: number, exercise: Exercise) {
    mutateBlock(sessionIndex, blockIndex, (block) => ({
      ...block,
      exercises: [...block.exercises, {
        exerciseId: exercise.id,
        exerciseName: exercise.name,
        prescription: "",
        optional: false,
      }],
    }));
  }
`,
`  function addBlankExercise(sessionIndex: number, blockIndex: number) {
    mutateBlock(sessionIndex, blockIndex, (block) => ({
      ...block,
      exercises: [...block.exercises, emptyExercise()],
    }));
  }
`,
  "creación de ejercicio vacío",
);

replaceOnce(
`                          <ExerciseEditor
                            blockIndex={blockIndex}
                            exercise={exercise}
`,
`                          <ExerciseEditor
                            blockIndex={blockIndex}
                            catalogExercises={exercisePage}
                            exercise={exercise}
`,
  "catálogo en editor",
);

replaceOnce(
`                        <div className="workout-add-exercise">
                          <select
                            aria-label={\`Agregar ejercicio de catálogo al bloque \${blockIndex + 1}\`}
                            className="rounded-lg border border-[#3b604b] bg-emerald-50/50 px-3 py-2 text-sm font-bold text-[#1e382b] transition-all hover:bg-emerald-100/70"
                            defaultValue=""
                            onChange={(event) => {
                              const found = exercisePage.find((item) => item.id === event.target.value);
                              if (found) addCatalogExercise(sessionIndex, blockIndex, found);
                              event.target.value = "";
                            }}
                          >
                            <option value="">➕ Agregar ejercicio del catálogo…</option>
                            {exercisePage.map((exercise) => (
                              <option key={exercise.id} value={exercise.id}>{exercise.name}</option>
                            ))}
                          </select>
                        </div>
`,
`                        <div className="workout-add-exercise">
                          <button
                            aria-label={\`Agregar ejercicio vacío al bloque \${blockIndex + 1}\`}
                            className="rounded-lg border border-[#3b604b] bg-emerald-50/50 px-3 py-2 text-sm font-bold text-[#1e382b] transition-all hover:bg-emerald-100/70"
                            onClick={() => addBlankExercise(sessionIndex, blockIndex)}
                            type="button"
                          >
                            + Agregar ejercicio
                          </button>
                        </div>
`,
  "botón agregar ejercicio",
);

replaceOnce(
`function ExerciseEditor({
  blockIndex,
  exercise,
`,
`function ExerciseEditor({
  blockIndex,
  catalogExercises,
  exercise,
`,
  "propiedad de catálogo",
);

replaceOnce(
`}: {
  blockIndex: number;
  exercise: WorkoutExerciseDraft;
`,
`}: {
  blockIndex: number;
  catalogExercises: Exercise[];
  exercise: WorkoutExerciseDraft;
`,
  "tipo de catálogo",
);

replaceOnce(
`    <details className="workout-exercise-card" open={exerciseIndex === 0 || undefined}>`,
`    <details className="workout-exercise-card" open={exerciseIndex === 0 || !exercise.exerciseName.trim() || undefined}>`,
  "apertura del ejercicio nuevo",
);

replaceOnce(
`            <input
              className="app-input"
              list={listId}
              onChange={(event) => onChange({ exerciseName: event.target.value, exerciseId: null })}
              value={exercise.exerciseName}
            />
`,
`            <input
              autoFocus={!exercise.exerciseName.trim()}
              className="app-input"
              list={listId}
              onChange={(event) => {
                const value = event.target.value;
                const normalized = value.trim().localeCompare.bind(value.trim());
                const match = catalogExercises.find((item) =>
                  normalized(item.name.trim(), "es-MX", { sensitivity: "base" }) === 0,
                );
                onChange({
                  exerciseName: value,
                  exerciseId: match?.id ?? null,
                  equipment: match?.equipment ?? null,
                  primaryMuscleGroup: match?.primaryMuscleGroup ?? null,
                  movementPattern: match?.movementPattern ?? null,
                });
              }}
              placeholder="Escribe para buscar en el catálogo"
              value={exercise.exerciseName}
            />
`,
  "autocompletado y metadatos",
);

replaceOnce(
`                <Field label="Indicación complementaria">
                  <input className="app-input" onChange={(event) => onChange({ prescription: event.target.value })} placeholder="Subir carga si completa todas las repeticiones" value={exercise.prescription} />
                </Field>
`,
`                <Field
                  help="Variante, agarre, equipo, RPE, RIR o condición que completa las series y repeticiones."
                  label="Complemento de la prescripción"
                >
                  <input className="app-input" onChange={(event) => onChange({ prescription: event.target.value })} placeholder="Ej. con mancuernas · agarre neutro · RIR 2" value={exercise.prescription} />
                </Field>
`,
  "complemento de prescripción",
);

replaceOnce(
`                <Field label="Notas técnicas">
                  <input className="app-input" onChange={(event) => onChange({ notes: event.target.value })} value={exercise.notes ?? ""} />
                </Field>
`,
`                <Field
                  help="Postura, ejecución, errores que deben evitarse, molestias o adaptaciones del movimiento."
                  label="Indicaciones técnicas y seguridad"
                >
                  <input className="app-input" onChange={(event) => onChange({ notes: event.target.value })} placeholder="Ej. mantener los codos fijos y detener si aparece dolor" value={exercise.notes ?? ""} />
                </Field>
`,
  "indicaciones técnicas",
);

replaceOnce(
`  async function downloadPdf(
    assignment: AssignmentReport["details"][number],
    template: "COMPACT_LIGHT" | "COMPACT_DARK" | "DETAILED" = "COMPACT_LIGHT"
  ) {
`,
`  async function downloadPdf(assignment: AssignmentReport["details"][number]) {
`,
  "firma de descarga",
);

replaceOnce(
`      await downloadWorkoutPdf(session.accessToken, assignment.id, template);
      onMessage(\`PDF (\${template === "COMPACT_LIGHT" ? "Claro" : template === "COMPACT_DARK" ? "Oscuro" : "Detallado"}) descargado.\`);
`,
`      await downloadWorkoutPdf(session.accessToken, assignment.id, "COMPACT_DARK");
      onMessage("PDF descargado.");
`,
  "descarga oscura predeterminada",
);

replaceOnce(
`                {canDownloadPdf ? (
                  <div className="inline-flex items-center rounded-lg border border-[#9eb2a5] bg-white p-0.5 shadow-sm">
                    <button
                      className="px-2.5 py-1.5 text-xs font-bold text-[#1e382b] hover:bg-emerald-50 rounded transition-all disabled:opacity-60"
                      disabled={downloading === assignment.id}
                      onClick={() => downloadPdf(assignment, "COMPACT_LIGHT")}
                      title="PDF para impresión en papel (fondo claro)"
                      type="button"
                    >
                      {downloading === assignment.id ? "Descargando…" : "📄 PDF Claro"}
                    </button>
                    <span className="text-stone-300 px-0.5">|</span>
                    <button
                      className="px-2.5 py-1.5 text-xs font-bold text-slate-800 hover:bg-stone-100 rounded transition-all disabled:opacity-60"
                      disabled={downloading === assignment.id}
                      onClick={() => downloadPdf(assignment, "COMPACT_DARK")}
                      title="PDF visual oscuro para celular"
                      type="button"
                    >
                      🌙 PDF Oscuro
                    </button>
                  </div>
                ) : null}
`,
`                {canDownloadPdf ? (
                  <button
                    className="rounded-lg border border-[#9eb2a5] bg-white px-3 py-2 text-sm font-bold text-[#1e382b] transition-all hover:bg-emerald-50 disabled:opacity-60"
                    disabled={downloading === assignment.id}
                    onClick={() => downloadPdf(assignment)}
                    title="Descargar rutina en PDF"
                    type="button"
                  >
                    {downloading === assignment.id ? "Descargando…" : "Descargar PDF"}
                  </button>
                ) : null}
`,
  "botón único PDF",
);

replaceOnce(
`function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="grid min-w-0 gap-1 text-sm font-medium text-[#435249]">
      <span>{label}</span>
      {children}
    </label>
  );
}
`,
`function Field({ label, help, children }: { label: string; help?: string; children: React.ReactNode }) {
  return (
    <label className="grid min-w-0 gap-1 text-sm font-medium text-[#435249]">
      <span>{label}</span>
      {help ? <small className="text-xs font-normal leading-relaxed text-[#6b7d72]">{help}</small> : null}
      {children}
    </label>
  );
}
`,
  "ayuda de campos",
);

fs.writeFileSync(pagePath, source, "utf8");
console.log("Interfaz nativa de Coaches aplicada.");
