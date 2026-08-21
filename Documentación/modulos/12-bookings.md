# Reservas y recursos

Estado: `FUNCIONAL`
Última revisión: 21 de agosto de 2026

## Implementado

- recursos reservables y horarios;
- disponibilidad y calendario mensual;
- reservas elegibles en sucursales secundarias para planes multi-sucursal y
  pago Stripe cuando el recurso requiere cobro;
- creación y transiciones de reserva;
- restricciones por plan/membresía;
- vínculo de pago y operación desde recepción;
- contexto por sucursal.

## Pendiente

- E2E de colisiones, cancelación, pago y otra sucursal (`GEN-004`);
- accesibilidad y cobertura de bordes horarios;
- autorización negativa completa (`GEN-001`).
