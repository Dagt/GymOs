# Frontend de GymFort

Aplicación Next.js 16 y React 19 para las consolas de plataforma, dueño,
gerencia, recepción, coaches y kiosco. La marca permanente GymFort convive con
la identidad visual configurable de cada gimnasio.

La interfaz usa un `AppShell` común, páginas administrativas oscuras coherentes
y notificaciones flotantes globales para éxito, advertencia y error. Los flujos
de Stripe redirigen a Checkout alojado y regresan a una pantalla con el mismo
sistema visual; el redirect nunca se considera comprobante de pago.

## Desarrollo

```powershell
npm.cmd ci
npm.cmd run dev
```

Copiar `.env.example` a `.env.local` cuando se ejecuta fuera de Docker. Nunca
exponer secretos del backend en variables `NEXT_PUBLIC_*`.

## Calidad

```powershell
npm.cmd test
npm.cmd run lint
npm.cmd run typecheck
npm.cmd run build
npm.cmd run test:e2e
```

Los E2E viven en `e2e/`. La URL puede sustituirse con
`PLAYWRIGHT_BASE_URL`; por defecto se espera el frontend local.

## Rutas principales

- `/platform/gymnasiums`: administración SaaS.
- `/dashboard`, `/operations`, `/reception`: consolas internas por rol.
- `/clients`, `/sales`, `/catalog`, `/inventory`, `/bookings`: operación.
- `/coaches`, `/wellness`, `/automations`, `/reports`: módulos avanzados.
- `/settings/organization`: cobros, kiosco, marca y correo.
- `/kiosk/launch`: apertura restringida a Recepción.
- `/kiosk/reception`: sesión aislada para socios.
