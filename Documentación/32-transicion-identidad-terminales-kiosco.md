# Transición de identidad de terminales del kiosco

Estado: DECISIÓN TRANSITORIA DOCUMENTADA  
Rama de referencia: `feature/automations-self-service-kiosk`

## 1. Problema corregido

El acceso al kiosco desde recepción utiliza temporalmente la ruta
`/kiosk/KIOSK-MAIN`. Antes de la migración V61, una organización sin terminal
propio podía terminar resolviendo el registro `KIOSK-MAIN` perteneciente al
tenant demo. La sesión del kiosco quedaba entonces asociada a otra organización
y la verificación de socios fallaba aunque los datos fueran correctos.

La corrección inmediata tiene dos reglas:

1. cada organización existente debe tener su propio registro temporal
   `KIOSK-MAIN`;
2. la resolución iniciada desde recepción debe conservar el contexto de la
   organización y sucursal activas y nunca degradarse silenciosamente a un
   terminal de otro tenant.

## 2. Solución temporal para pruebas

La migración `V61__provision_default_kiosk_per_organization.sql` crea un
terminal activo `KIOSK-MAIN` en cada organización que todavía no tenga uno.

Para organizaciones con una sola sucursal, el terminal queda asociado a esa
sucursal. Para organizaciones con varias sucursales, la migración selecciona
temporalmente la primera sucursal activa ordenada por `created_at` e `id`.

Esta selección sólo permite ejecutar las pruebas funcionales actuales. No
representa una decisión comercial ni el modelo definitivo para cadenas de
gimnasios.

## 3. Alcance y limitaciones de `KIOSK-MAIN`

`KIOSK-MAIN` es una etiqueta temporal por organización:

- no es globalmente única;
- no identifica inequívocamente una instalación física;
- no es una credencial;
- no demuestra que la solicitud provenga de un dispositivo provisionado;
- no permite distinguir varios kioscos dentro de una organización o sucursal;
- depende del contexto enviado por recepción para evitar ambigüedad entre
  tenants.

No se deben construir nuevas integraciones suponiendo que `KIOSK-MAIN` será la
identidad definitiva del terminal.

## 4. Diseño definitivo requerido

El modelo objetivo debe representar cada terminal físico como una entidad
independiente. Cada terminal deberá tener como mínimo:

- UUID interno;
- `publicTerminalId` aleatorio y globalmente único;
- URL pública derivada del identificador global;
- organización y sucursal obligatorias;
- nombre visible y ubicación operativa;
- credencial o secreto de activación rotatable almacenado de forma segura;
- estado `PENDING_ACTIVATION`, `ACTIVE`, `SUSPENDED` o `REVOKED`;
- capacidades configurables;
- fecha de último contacto, versión del cliente y telemetría mínima;
- auditoría de alta, activación, rotación, suspensión y revocación.

La relación esperada es:

- una organización puede tener varias sucursales;
- una sucursal puede tener uno o varios terminales;
- cada terminal tiene una URL pública globalmente única;
- cada dispositivo utiliza su propia credencial;
- conocer la URL o la etiqueta humana no debe ser suficiente para suplantar el
  dispositivo.

Ejemplo conceptual:

```text
Sucursal Centro
  /kiosk/7d4c2c5e-...  Terminal recepción
  /kiosk/5f0b1a91-...  Terminal vestidores

Sucursal Norte
  /kiosk/e32a9014-...  Terminal entrada
```

## 5. Criterios para retirar la solución temporal

`KIOSK-MAIN` podrá dejar de utilizarse cuando:

- exista CRUD administrativo de terminales;
- cada instalación tenga identificador público global;
- el dispositivo pueda activarse y recibir una credencial propia;
- la creación de sesión valide la identidad provisionada del terminal;
- recepción abra el terminal configurado para su sucursal, no un código fijo;
- existan pruebas E2E con varias organizaciones, varias sucursales y varios
  terminales;
- la migración de los registros temporales a terminales definitivos esté
  documentada y probada.

Hasta entonces, `V61` mantiene una compatibilidad controlada para pruebas y
debe conservarse como una medida transitoria explícita.
