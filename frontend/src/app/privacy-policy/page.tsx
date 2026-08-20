import type { Metadata } from "next";
import { PublicInfoLayout } from "@/components/PublicInfoLayout";

export const metadata: Metadata = {
  title: "Aviso de privacidad",
  description: "Conoce cómo GymFort recopila, utiliza y protege los datos personales de sus clientes y usuarios.",
  alternates: { canonical: "/privacy-policy" },
};

export default function PrivacyPolicyPage() {
  return (
    <PublicInfoLayout
      eyebrow="Información legal"
      title="Aviso de privacidad"
      description="Explicamos con claridad qué información tratamos, para qué la utilizamos y cómo puedes ejercer tus derechos."
      updated="20 de agosto de 2026"
    >
      <section className="public-info-summary">
        <strong>Resumen</strong>
        <p>GymFort utiliza la información necesaria para prestar y proteger su plataforma. No vendemos datos personales ni almacenamos números completos de tarjetas bancarias.</p>
      </section>

      <h2>1. Responsable y alcance</h2>
      <p>GymFort, disponible en <a href="https://gymfort.mx">gymfort.mx</a>, es responsable del tratamiento de los datos personales que obtiene directamente para administrar cuentas, relaciones comerciales, soporte y el uso de su sitio web.</p>
      <p>Cuando un gimnasio utiliza GymFort para administrar información de sus miembros, empleados o proveedores, ese gimnasio determina los fines del tratamiento y GymFort actúa como proveedor tecnológico conforme a sus instrucciones y a los acuerdos aplicables.</p>

      <h2>2. Información que podemos recopilar</h2>
      <ul>
        <li>Datos de identificación y contacto, como nombre, correo electrónico y teléfono.</li>
        <li>Datos de cuenta, organización, sucursal, rol y permisos.</li>
        <li>Información comercial, de facturación y estado de suscripción.</li>
        <li>Registros técnicos y de seguridad, como dirección IP, dispositivo, fecha de acceso y acciones relevantes dentro de la plataforma.</li>
        <li>Mensajes y archivos que nos envíes al solicitar soporte.</li>
        <li>Información operativa que el gimnasio decida incorporar al servicio.</li>
      </ul>

      <h2>3. Pagos</h2>
      <p>Los pagos pueden ser procesados por Stripe u otros proveedores autorizados. GymFort no almacena números completos de tarjeta ni códigos de seguridad. El proveedor de pagos trata la información conforme a sus propias políticas y estándares de seguridad aplicables.</p>

      <h2>4. Finalidades del tratamiento</h2>
      <p>Tratamos la información para:</p>
      <ul>
        <li>Crear, autenticar y proteger cuentas.</li>
        <li>Prestar las funciones contratadas y mantener la continuidad del servicio.</li>
        <li>Procesar cobros, emitir comprobantes y administrar suscripciones.</li>
        <li>Atender solicitudes de soporte, seguridad, privacidad y cancelación.</li>
        <li>Prevenir fraude, abuso, accesos no autorizados y errores técnicos.</li>
        <li>Cumplir obligaciones legales y hacer valer nuestros términos.</li>
        <li>Mejorar el desempeño y la experiencia de la plataforma mediante información agregada o desidentificada.</li>
      </ul>

      <h2>5. Proveedores y transferencias</h2>
      <p>Podemos compartir la información estrictamente necesaria con proveedores de infraestructura en la nube, almacenamiento, comunicaciones, monitoreo, seguridad y pagos. Estos proveedores deben tratarla únicamente para prestar sus servicios y aplicar medidas razonables de confidencialidad y seguridad.</p>
      <p>También podremos revelar información cuando exista una obligación legal, una solicitud válida de autoridad o sea necesario para proteger derechos, personas o la seguridad de la plataforma.</p>

      <h2>6. Conservación</h2>
      <p>Conservamos la información mientras exista una cuenta o relación comercial y durante el periodo adicional necesario para atender obligaciones legales, fiscales, de seguridad, respaldo y resolución de controversias. Después se elimina, anonimiza o bloquea de forma segura conforme a la finalidad aplicable.</p>

      <h2>7. Seguridad</h2>
      <p>Aplicamos controles de acceso, autenticación, separación lógica por organización, registros de actividad y medidas técnicas y organizativas razonables. Ningún sistema es completamente infalible; si identificas una posible vulnerabilidad, repórtala de inmediato en nuestro <a href="/support">centro de soporte</a>.</p>

      <h2>8. Cookies y almacenamiento local</h2>
      <p>Podemos utilizar cookies o almacenamiento del navegador indispensables para mantener una sesión, recordar preferencias y proteger la aplicación. Si incorporamos tecnologías no esenciales de analítica o publicidad, informaremos sus finalidades y ofreceremos los controles exigidos por la normativa aplicable.</p>

      <h2>9. Derechos sobre tus datos</h2>
      <p>Puedes solicitar acceso, rectificación, cancelación, oposición, limitación o eliminación de tus datos, según corresponda. Envía la solicitud a <a href="mailto:contacto@gymfort.mx?subject=Solicitud%20de%20privacidad">contacto@gymfort.mx</a> indicando tu nombre, organización, derecho que deseas ejercer y la información necesaria para verificar tu identidad.</p>
      <p>Si tu solicitud se relaciona con información administrada por un gimnasio cliente, podremos remitirte a esa organización para que atienda el ejercicio de tus derechos como responsable del tratamiento.</p>

      <h2>10. Menores de edad</h2>
      <p>GymFort es un servicio empresarial y no está dirigido a menores para que contraten directamente. Los gimnasios clientes son responsables de contar con la autorización y base legal necesarias cuando administren información de menores de edad.</p>

      <h2>11. Cambios a este aviso</h2>
      <p>Podremos actualizar este aviso para reflejar cambios legales, técnicos o del servicio. Publicaremos la versión vigente en esta misma URL e indicaremos su fecha de actualización.</p>

      <h2>12. Contacto</h2>
      <p>Para preguntas o solicitudes de privacidad, escribe a <a href="mailto:contacto@gymfort.mx">contacto@gymfort.mx</a>. También puedes consultar las opciones disponibles en <a href="/support">gymfort.mx/support</a>.</p>
    </PublicInfoLayout>
  );
}
