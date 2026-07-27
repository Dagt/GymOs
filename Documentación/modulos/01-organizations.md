# Organizaciones y sucursales

Estado: `FUNCIONAL`
Código: `backend/.../organizations`, `/platform/gymnasiums`, `/settings/organization`, `/users`

## Implementado

- organizaciones multi-tenant y múltiples sucursales;
- consola separada de plataforma y tenant;
- selección explícita de gimnasio y sucursal;
- alta y actualización de sucursales;
- administración de equipo con alcance por organización;
- tabla visual compacta y responsiva para usuarios, con sucursales, rol, estado
  y acciones alineadas sin separar a los coaches del resto del equipo;
- alta integral del Coach y edición de sus datos adicionales mediante panel
  lateral dentro del mismo flujo de identidad.

## Pendiente

- matriz negativa exhaustiva de aislamiento por UUID y sucursal (`GEN-001`);
- cobertura CRUD completa de usuarios y sucursales;
- retirar accesos directos a infraestructura que queden en application (`GEN-002`).

Última evidencia: `d1d5124`; 31 pruebas frontend, typecheck, lint, build y 8 E2E
de Coaches/Equipo exitosos, 26-jul-2026.
