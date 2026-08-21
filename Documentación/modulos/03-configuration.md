# Configuración y marca

Estado: `FUNCIONAL`
Última revisión: 21 de agosto de 2026

## Implementado

- nombre, marca y logotipo por organización;
- configuración diferenciada de plataforma y tenant;
- cobros, capacidades de kiosco y remitente de correo por permisos separados;
- centro de control oscuro con resumen, servicios conectados segmentados y
  navegación operativa fija, más feedback mediante notificaciones flotantes;
- atribución GymFort separada de la tarjeta de marca tenant y de la identidad
  de la sesión para evitar saturación en el menú lateral;
- propagación de marca a consola, recepción y kiosco;
- carga y reemplazo de PNG/JPG/WebP de hasta 2 MB en almacenamiento de objetos,
  con URL versionada y proxy público de sólo lectura;
- borradores aislados por organización y sucursal.

## Pendiente

- pruebas de permisos y aislamiento específicas;
- eliminación explícita del logotipo y cola durable para reintentar la limpieza
  de objetos huérfanos (`CFG-001`);
- revisar defaults y accesibilidad visual.
