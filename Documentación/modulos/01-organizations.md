# Organizaciones y sucursales

Estado: `FUNCIONAL`
Última revisión: 26 de agosto de 2026
Código: `backend/.../organizations`, `/platform/gymnasiums`, `/settings/organization`, `/users`

## Implementado

- organizaciones multi-tenant y múltiples sucursales;
- identidad visible GymFort permanente junto a la marca configurable del tenant;
- consola separada de plataforma y tenant;
- selección explícita de gimnasio y sucursal;
- alta y actualización de sucursales;
- `organization.read` existe desde Flyway y se asigna a todas las plantillas
  de dueño, incluidas instalaciones creadas desde cero; la lectura de
  `/organizations/current` también acepta `organization.update` para mantener
  coherente el contrato de administración;
- carga de logotipo autenticada, aislada por organización, auditada y
  persistida en almacenamiento de objetos;
- el alta de plataforma previsualiza el archivo y lo carga sólo después de
  crear el tenant mediante el endpoint administrado; ya no escribe en
  `frontend/public` ni invoca el endpoint local heredado;
- administración de equipo con alcance por organización;
- tabla visual compacta y responsiva para usuarios, con sucursales, rol, estado
  y acciones alineadas sin separar a los coaches del resto del equipo;
- alta integral del Coach y edición de sus datos adicionales mediante panel
  lateral dentro del mismo flujo de identidad.

## Pendiente

- permitir que un solo tenant tenga perfiles de marca distintos por sucursal,
  con la organización como fallback; las sucursales siguen compartiendo socios,
  catálogo, planes y beneficios;
- matriz negativa exhaustiva de aislamiento por UUID y sucursal (`GEN-001`);
- cobertura CRUD completa de usuarios y sucursales;
- retirar accesos directos a infraestructura que queden en application (`GEN-002`).

Última evidencia: 241 pruebas backend y 56 frontend sin fallos,
`DatabaseMigrationTests`, `SecurityAuthorizationTests`,
`OrganizationLogoServiceTests`, typecheck, build, E2E de alta inicial con logo y
smoke en Chromium del perfil de dueño sin `organization.read`, 30-jul-2026.
