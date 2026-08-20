import type { Metadata } from "next";
import { PublicInfoLayout } from "@/components/PublicInfoLayout";

export const metadata: Metadata = {
  title: "Términos de servicio",
  description: "Condiciones de contratación, entrega, cancelación y uso de la plataforma GymFort.",
  alternates: { canonical: "/terms-of-service" },
};

export default function TermsOfServicePage() {
  return (
    <PublicInfoLayout
      eyebrow="Condiciones del servicio"
      title="Términos de servicio"
      description="Estas condiciones explican cómo se contrata, entrega y utiliza GymFort, así como nuestras políticas de cobro, cancelación y reembolso."
      updated="20 de agosto de 2026"
    >
      <section className="public-info-summary">
        <strong>Servicio digital para empresas</strong>
        <p>GymFort es una plataforma de software para gimnasios y centros deportivos. No enviamos productos físicos; el acceso se entrega electrónicamente.</p>
      </section>

      <h2>1. Aceptación y representación</h2>
      <p>Al contratar, acceder o utilizar GymFort, aceptas estos términos y confirmas que tienes capacidad legal y autorización para representar al gimnasio, empresa u organización asociada con la cuenta. Si no estás de acuerdo, no debes utilizar el servicio.</p>

      <h2>2. Descripción del servicio</h2>
      <p>GymFort permite administrar, según el plan y configuración contratados, miembros, membresías, accesos, ventas, inventario, reservas, sucursales, usuarios, automatizaciones y reportes. Las funciones específicas se indican en la propuesta, orden, periodo de prueba o pantalla de contratación correspondiente.</p>

      <h2>3. Cuenta y uso autorizado</h2>
      <p>El cliente es responsable de proporcionar información correcta, proteger sus credenciales, asignar permisos apropiados y mantener actualizados sus usuarios. Las acciones realizadas desde una cuenta se consideran autorizadas hasta que el cliente notifique un posible acceso indebido.</p>

      <h2>4. Precios, moneda e impuestos</h2>
      <p>Los precios, periodicidad, impuestos y funciones incluidas se muestran antes del pago o se establecen en una propuesta comercial aceptada. Salvo que la pantalla de pago o propuesta indique expresamente otra moneda, los cargos se presentan y procesan en pesos mexicanos (MXN).</p>
      <p>Los descuentos, pruebas gratuitas o promociones se rigen por las condiciones comunicadas al momento de aceptarlos. Al terminar una prueba, no se realizará un cargo automático salvo que el cliente haya aceptado previamente el precio y autorizado el método de pago.</p>

      <h2>5. Facturación y renovación</h2>
      <p>Cuando se contrata una suscripción recurrente, el cliente autoriza el cobro al método de pago registrado al inicio de cada periodo. La suscripción se renueva por la misma periodicidad hasta que sea cancelada. Si un cobro falla, podremos notificarlo, reintentarlo o limitar el acceso hasta regularizar la cuenta.</p>

      <h2>6. Entrega y activación</h2>
      <p>GymFort es un servicio digital. Después de aceptar la contratación y, cuando corresponda, confirmar el pago, las credenciales o instrucciones de acceso se envían electrónicamente al administrador designado en la fecha de activación acordada. No existen costos de envío, entrega física ni devoluciones de mercancía.</p>

      <h2>7. Cancelación</h2>
      <p>El cliente puede solicitar la cancelación en cualquier momento antes de la siguiente renovación escribiendo a <a href="mailto:contacto@gymfort.mx?subject=Cancelación%20de%20GymFort">contacto@gymfort.mx</a>. Debe indicar el gimnasio, correo de la cuenta administradora y motivo de la solicitud para verificar la autorización.</p>
      <p>La cancelación detiene renovaciones futuras. El acceso continúa hasta el final del periodo ya pagado, salvo que el cliente solicite el cierre inmediato o exista una causa de suspensión conforme a estos términos.</p>

      <h2>8. Política de reembolsos</h2>
      <p>Debido a que GymFort es un servicio digital empresarial, los periodos ya iniciados y el tiempo no utilizado no son reembolsables. Sí revisaremos solicitudes relacionadas con cargos duplicados, importes distintos a los autorizados o servicios que no hayan podido ser activados por una causa atribuible exclusivamente a GymFort.</p>
      <p>Para solicitar una revisión, escribe a <a href="mailto:contacto@gymfort.mx?subject=Solicitud%20de%20reembolso">contacto@gymfort.mx</a> con el identificador del cargo y una descripción del caso. Si el reembolso procede, se enviará al método de pago original. Esta política no limita derechos irrenunciables reconocidos por la legislación aplicable.</p>

      <h2>9. Uso prohibido</h2>
      <p>No se permite utilizar GymFort para actividades ilícitas; vulnerar cuentas o sistemas; introducir código malicioso; eludir límites o controles; extraer información sin autorización; afectar la disponibilidad del servicio; revenderlo sin autorización; o almacenar datos para los que el cliente no tenga una base legal.</p>

      <h2>10. Datos del cliente y privacidad</h2>
      <p>El cliente conserva sus derechos sobre la información que incorpora y es responsable de obtener las autorizaciones necesarias. GymFort la procesa para prestar, proteger y dar soporte al servicio conforme al <a href="/privacy-policy">aviso de privacidad</a> y los acuerdos aplicables.</p>

      <h2>11. Pagos y seguridad</h2>
      <p>Los pagos pueden procesarse mediante Stripe u otro proveedor autorizado. GymFort no almacena números completos de tarjeta ni códigos de seguridad. El cliente debe utilizar los flujos de pago autorizados y notificarnos de inmediato cualquier cargo que no reconozca.</p>

      <h2>12. Propiedad intelectual</h2>
      <p>GymFort, su software, identidad, interfaces, documentación y componentes son propiedad de sus titulares y están protegidos por la legislación aplicable. La contratación concede al cliente un derecho limitado, no exclusivo, no transferible y revocable para utilizar el servicio durante la vigencia contratada.</p>

      <h2>13. Disponibilidad y cambios</h2>
      <p>Trabajamos para mantener la plataforma disponible y segura, pero pueden existir mantenimientos, actualizaciones, fallas de terceros o eventos fuera de nuestro control. Podemos modificar funciones para mejorar seguridad, desempeño o cumplimiento, procurando no reducir materialmente el servicio contratado durante el periodo vigente.</p>

      <h2>14. Suspensión y terminación</h2>
      <p>Podemos suspender el acceso por falta de pago, riesgo de seguridad, uso ilícito, abuso o incumplimiento material. Cuando sea razonable, notificaremos al cliente y ofreceremos oportunidad de corregir la causa antes de una terminación definitiva.</p>

      <h2>15. Responsabilidad</h2>
      <p>GymFort apoya la gestión administrativa, pero no sustituye asesoría legal, fiscal, contable, médica ni profesional. En la medida permitida por la ley, no responderemos por daños indirectos, pérdida de oportunidades o decisiones tomadas exclusivamente con base en reportes de la plataforma. Nada en estos términos excluye responsabilidades que legalmente no puedan limitarse.</p>

      <h2>16. Legislación aplicable</h2>
      <p>Estos términos se interpretan conforme a las leyes aplicables en México. Antes de iniciar una controversia, las partes procurarán resolverla de buena fe mediante los canales indicados en el centro de soporte.</p>

      <h2>17. Cambios y contacto</h2>
      <p>Podremos actualizar estos términos y publicaremos la versión vigente en esta URL. Para preguntas comerciales, soporte, cancelaciones o aclaraciones de pago, visita <a href="/support">gymfort.mx/support</a> o escribe a <a href="mailto:contacto@gymfort.mx">contacto@gymfort.mx</a>.</p>
    </PublicInfoLayout>
  );
}
