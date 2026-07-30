# Organizaciones y sucursales

Estado: `FUNCIONAL`
Código: `backend/.../organizations`, `/platform/gymnasiums`, `/settings/organization`, `/users`

## Implementado

- organizaciones multi-tenant y múltiples sucursales;
- consola separada de plataforma y tenant;
- selección explícita de gimnasio y sucursal;
- alta y actualización de sucursales;
- `organization.read` existe desde Flyway y se asigna a todas las plantillas
  de dueño, incluidas instalaciones creadas desde cero; la lectura de
  `/organizations/current` también acepta `organization.update` para mantener
  coherente el contrato de administración;
- carga de logotipo autenticada, aislada por organización, auditada y
  persistida en almacenamiento de objetos;
- administración de equipo con alcance por organización;
- tabla visual compacta y responsiva para usuarios, con sucursales, rol, estado
  y acciones alineadas sin separar a los coaches del resto del equipo;
- alta integral del Coach y edición de sus datos adicionales mediante panel
  lateral dentro del mismo flujo de identidad.

## Pendiente

- matriz negativa exhaustiva de aislamiento por UUID y sucursal (`GEN-001`);
- cobertura CRUD completa de usuarios y sucursales;
- retirar accesos directos a infraestructura que queden en application (`GEN-002`).

Última evidencia: 237 pruebas backend y 54 frontend sin fallos,
`DatabaseMigrationTests`, `SecurityAuthorizationTests`,
`OrganizationLogoServiceTests`, typecheck, build y smoke en Chromium del perfil
de dueño sin `organization.read`, 30-jul-2026.
