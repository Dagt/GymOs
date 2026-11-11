# Configuración y marca

Estado: `FUNCIONAL`
Última revisión: 26 de agosto de 2026

## Implementado

- nombre, marca y logotipo por organización;
- configuración diferenciada de plataforma y tenant;
- cobros, capacidades de kiosco y remitente de correo por permisos separados;
- sección `Inscripciones` dentro de `/settings/organization`, con inscripción y baja
  automática gobernadas por un único `inactivityMonths`; periodo y baja
  automática comparten un bloque compacto y los valores pueden configurarse
  sin encender la función;
- autosave serializado de toggles de kiosco y medios de cobro, y autosave con
  debounce para todos los valores de inscripción, con rollback visible ante
  error y sin botón global;
- capacidades de kiosco persistidas como intención del gimnasio, independientes
  de la disponibilidad técnica actual de Stripe;
- resumen y servicios conectados sobre superficies claras compartidas con el
  resto de la administración, navegación operativa fija y feedback mediante
  notificaciones flotantes;
- identidad tenant prioritaria en la parte superior del menú, sesión debajo y
  atribución GymFort discreta en el pie para evitar saturación;
- propagación de marca a consola, recepción y kiosco;
- carga y reemplazo de PNG/JPG/WebP de hasta 2 MB en almacenamiento de objetos,
  con URL versionada y proxy público de sólo lectura;
- borradores aislados por organización y sucursal.

## Pendiente

- perfil de marca por sucursal (`brandName`, logo y colores) con fallback de la
  organización y propagación a consola, recepción, kiosco, correos, recibos y
  PDFs (`CFG-003`);
- pruebas de permisos y aislamiento específicas;
- eliminación explícita del logotipo y cola durable para reintentar la limpieza
  de objetos huérfanos (`CFG-001`);
- revisar defaults y accesibilidad visual.

## Contratos de configuración vigentes

`inactivityMonths` es el único umbral temporal para determinar periodo de
inactividad y reinscripción. `registrationFeeEnabled` y
`autoInactivationEnabled` continúan siendo funciones independientes.

En kiosco, CONFIGURED expresa lo que el gimnasio desea ofrecer, READY expresa
la disponibilidad técnica del proveedor y EFFECTIVE exige ambas condiciones al
ejecutar. Un ajuste configurado no se pierde mientras Stripe está pendiente.
El indicador de dependencia sólo se muestra para capacidades configuradas en
`true`; una capacidad apagada se describe como deshabilitada.

PR #74: paleta y logo confirmados actualizan la sesión y el tema sin navegar; el proveedor escucha los eventos de sesión y descarta respuestas de un contexto anterior. Evidencia en docs/testing/tenant-theme-session-sync.md.
