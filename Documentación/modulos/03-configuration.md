# Configuración y marca

Estado: `FUNCIONAL`

## Implementado

- nombre, marca y logotipo por organización;
- configuración diferenciada de plataforma y tenant;
- propagación de marca a consola, recepción y kiosco;
- carga y reemplazo de PNG/JPG/WebP de hasta 2 MB en almacenamiento de objetos,
  con URL versionada y proxy público de sólo lectura;
- borradores aislados por organización y sucursal.

## Pendiente

- pruebas de permisos y aislamiento específicas;
- eliminación explícita del logotipo y cola durable para reintentar la limpieza
  de objetos huérfanos (`CFG-001`);
- revisar defaults y accesibilidad visual.
