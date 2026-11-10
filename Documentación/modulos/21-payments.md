# Payments

Stripe Checkout comprueba el estado de la sesión en Connect al recuperarla.
Cada reemplazo queda vinculado al intento anterior por su clave idempotente.
PostgreSQL serializa la reserva con la confirmación de la venta. Un resultado
incierto no abre otra sesión. Para creaciones sin identificador externo y
fuera de la ventana segura, consultar el historial de Stripe antes de actuar.

Checkout trata los eventos firmados de expiración y fallo asíncrono con una
lectura autoritativa de la cuenta Connect. Un intento terminal sin pago puede
volver a abrirse sobre la misma venta y el mismo pago. Setup vuelve a usar la
solicitud estable mientras se recupera de un timeout. Los reembolsos Stripe
siguen bloqueados en la ruta manual. Consulta la [matriz de
evidencia](../40-qa-matriz-de-aceptacion-y-evidencias.md#pay-001-y-self-001-34).

Estado: FUNCIONAL / LISTO PARA ACEPTACIÓN CONECTADA
Última revisión: 21 de agosto de 2026

## Implementado

- Configuración tenant para terminal manual de recepción, Mercado Pago Point,
  Stripe self checkout y Stripe recurrente.
- Terminal manual y Mercado Pago Point son modalidades excluyentes: activar una
  apaga la otra en la interfaz y el backend rechaza estados con ambas activas.
- Stripe self checkout gobierna las capacidades pagadas del kiosco; si está
  deshabilitado, compra, renovación y cambio de plan quedan bloqueados. La
  domiciliación del kiosco exige además Stripe recurrente habilitado.
- Conexiones de proveedor y credenciales cifradas fuera de los contratos web.
- Stripe Connect con cuenta Express y Account Links; Checkout alojado para
  contratación, renovación, cambio de plan y otros cobros del kiosco.
- Separación de permisos: `payment.provider.stripe.manage` conecta/refresca la
  cuenta; `payment.settings.manage` solo cambia modalidades operativas.
- Mercado Pago OAuth, selección de Point y operaciones idempotentes.
- Venta y pago pendientes antes de llamar al proveedor; nunca se aplican
  efectos financieros por el redirect del navegador.
- Webhooks firmados, inbox durable, deduplicación, conciliación de monto,
  moneda, tenant, sucursal, venta, pago y método.
- Recuperación de operaciones no terminales, recibos alojados por proveedor e
  historiales paginados de mandatos, cargos y webhooks.
- Domiciliación con consentimiento versionado, Setup alojado, identificadores
  opacos, marca/últimos cuatro, cargos off-session, reintentos, `PAST_DUE`,
  sustitución de tarjeta y revocación.
- Idempotencia para impedir ventas, pagos, beneficios o periodos duplicados por
  doble clic, timeout, reintento o webhook repetido.
- Métricas y alertas tenant-scoped sin PAN, CVV ni payloads sensibles.
- Perfil Docker opcional con `stripe/stripe-cli:v1.50.3`; CI valida su descarga
  y ejecución.

## Permisos y superficies

- Dueño y soporte de plataforma: conectar/refrescar Stripe y configurar modos.
- Gerente: activar/desactivar Checkout o recurrencia cuando la cuenta ya existe;
  no crear, sustituir ni verificar cuentas Stripe.
- Recepción: operar cobros permitidos y abrir el kiosco; no administrar cuentas.
- Socio en kiosco: solo Checkout/Setup alojado dentro de una sesión temporal.

Endpoints principales: `/api/v1/payment-settings`, historial y operaciones bajo
esa ruta, y webhooks públicos endurecidos en
`/api/v1/payment-webhooks/{stripe|mercado-pago}`.

## Evidencia

- Pruebas unitarias de configuración, firma, conciliación, idempotencia y
  recurrencia dentro de la suite backend, incluida la exclusividad entre
  terminal independiente y Mercado Pago y las dependencias Stripe del kiosco.
- Integración PostgreSQL opt-in mediante `postgresIntegrationTest`.
- Contrato Stripe opt-in mediante `stripeTest`, que rechaza llaves live.
- Recorridos manuales test de Checkout, retorno, webhook, recibo y domiciliación.
- Validación real de la imagen Stripe CLI fijada en el pipeline.

## Pendiente

- Ejecutar y publicar `stripeTest` conectado dentro de CI con secretos seguros.
- Completar cancelación/reembolso autoritativo para cada proveedor (`PAY-001`).
- Ampliar E2E de timeout, caída entre persistencia/confirmación y recuperación.

## Fuera de alcance

- Almacenamiento de PAN/CVV.
- Confirmación por redirect o por dato enviado por el navegador.
- Llaves Stripe en frontend, imágenes Docker o repositorio.

## SAL-001

Los webhooks se reclaman por evento e intento, verifican la cuenta u orden y aplican el pago con sus efectos locales en una transacción. Tras timeout de Point, recuperar la misma orden evita un segundo cobro.

Point compara la moneda de la orden consultada y el importe efectivamente pagado con los de la operación. Una respuesta insuficiente conserva la operación para conciliación y la venta pendiente. Stripe exige importes enteros en unidades menores, según sus contratos de Checkout y PaymentIntent.
