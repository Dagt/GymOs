# Organizaciones y sucursales

Estado: `FUNCIONAL`
Código: `backend/.../organizations`, `/platform/gymnasiums`, `/settings/organization`, `/users`

## Implementado

- organizaciones multi-tenant y múltiples sucursales;
- consola separada de plataforma y tenant;
- selección explícita de gimnasio y sucursal;
- alta y actualización de sucursales;
- administración de equipo con alcance por organización.

## Pendiente

- matriz negativa exhaustiva de aislamiento por UUID y sucursal (`GEN-001`);
- cobertura CRUD completa de usuarios y sucursales;
- retirar accesos directos a infraestructura que queden en application (`GEN-002`).

Última evidencia: suite backend completa y build frontend, 26-jul-2026.
