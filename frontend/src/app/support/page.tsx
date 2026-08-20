import type { Metadata } from "next";
import { PublicInfoLayout } from "@/components/PublicInfoLayout";

export const metadata: Metadata = {
  title: "Centro de soporte",
  description: "Contacta al equipo de GymFort para ayuda técnica, facturación, cancelaciones, privacidad o seguridad.",
  alternates: { canonical: "/support" },
};

const supportOptions = [
  {
    title: "Soporte técnico",
    description: "Acceso a la cuenta, funcionamiento de la plataforma, configuración y errores.",
    subject: "Soporte técnico GymFort",
    action: "Solicitar ayuda técnica",
  },
  {
    title: "Facturación y suscripción",
    description: "Aclaraciones de cargos, comprobantes, renovación, cancelación o reembolsos.",
    subject: "Facturación y suscripción GymFort",
    action: "Consultar facturación",
  },
  {
    title: "Privacidad y seguridad",
    description: "Derechos sobre datos personales, accesos no reconocidos o reportes de seguridad.",
    subject: "Privacidad y seguridad GymFort",
    action: "Contactar a privacidad",
  },
];

export default function SupportPage() {
  return (
    <PublicInfoLayout
      eyebrow="Estamos para ayudarte"
      title="Centro de soporte"
      description="Encuentra el canal adecuado para resolver dudas sobre la plataforma, tu cuenta o tu suscripción."
    >
      <section className="support-contact-card">
        <div>
          <span>Contacto directo</span>
          <h2>Escríbenos a contacto@gymfort.mx</h2>
          <p>Atendemos solicitudes en español. Los casos se revisan por orden de recepción y prioridad; incluye suficiente contexto para que podamos ayudarte sin retrasos.</p>
        </div>
        <a href="mailto:contacto@gymfort.mx?subject=Solicitud%20de%20soporte%20GymFort">Enviar correo <span>→</span></a>
      </section>

      <h2>¿En qué podemos ayudarte?</h2>
      <div className="support-options">
        {supportOptions.map((option) => (
          <section key={option.title}>
            <h3>{option.title}</h3>
            <p>{option.description}</p>
            <a href={`mailto:contacto@gymfort.mx?subject=${encodeURIComponent(option.subject)}`}>{option.action} →</a>
          </section>
        ))}
      </div>

      <h2>Información que debes incluir</h2>
      <p>Para identificar tu cuenta y atender el caso con mayor rapidez, agrega:</p>
      <ul>
        <li>Nombre del gimnasio y correo de la cuenta administradora.</li>
        <li>Descripción clara de lo que intentabas hacer y el resultado obtenido.</li>
        <li>Fecha aproximada, sucursal y sección de la plataforma involucrada.</li>
        <li>Capturas de pantalla sin contraseñas, datos completos de tarjeta ni información sensible innecesaria.</li>
        <li>En aclaraciones de pago, el identificador y fecha del cargo; nunca envíes el número completo de tu tarjeta.</li>
      </ul>

      <h2>Acceso y seguridad</h2>
      <p>Si sospechas que alguien accedió a tu cuenta, cambia tu contraseña, evita compartir credenciales y escríbenos con el asunto “Acceso no reconocido”. No solicitamos contraseñas ni códigos completos de tarjeta por correo.</p>

      <h2>Cancelaciones y reembolsos</h2>
      <p>Puedes solicitar la cancelación antes de la siguiente renovación. Consulta las condiciones completas y los casos en que revisamos un reembolso en nuestros <a href="/terms-of-service">términos de servicio</a>.</p>

      <h2>Privacidad</h2>
      <p>Para ejercer derechos sobre tus datos, utiliza el asunto “Solicitud de privacidad” e incluye la información necesaria para verificar tu identidad. Conoce el proceso en el <a href="/privacy-policy">aviso de privacidad</a>.</p>

      <section className="support-service-note">
        <strong>GymFort es un servicio digital.</strong>
        <p>La activación y el soporte se entregan por medios electrónicos. No realizamos envíos de productos físicos.</p>
      </section>
    </PublicInfoLayout>
  );
}
