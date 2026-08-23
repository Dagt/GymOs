"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useState } from "react";
import { clearSession, getStoredSession, type StoredSession } from "@/lib/api";
import { getPostLoginPath } from "@/lib/session-routing";
import styles from "./pricing.module.css";

const features = [
  ["01", "Miembros y membresías", "Expedientes, planes, renovaciones y seguimiento en un solo lugar."],
  ["02", "Accesos y autoservicio", "Controla entradas y ofrece una experiencia ágil desde el kiosco."],
  ["03", "Ventas e inventario", "Opera caja, productos, existencias y movimientos con trazabilidad."],
  ["04", "Reservas y entrenamiento", "Coordina espacios, clases, coaches y rutinas desde la misma plataforma."],
  ["05", "Reportes para decidir", "Consulta indicadores operativos y comerciales para actuar a tiempo."],
  ["06", "Operación multi-sucursal", "Separa equipos, permisos y datos sin perder una visión centralizada."],
];

const steps = [
  ["Configura", "Registra tu gimnasio, sucursales, equipo y catálogo."],
  ["Opera", "Gestiona la actividad diaria desde cualquier dispositivo."],
  ["Crece", "Automatiza tareas y toma decisiones con información ordenada."],
];

const plans = [
  { name: "GymFort 150", capacity: "Hasta 150 socios activos", price: "1,190", tag: "Gimnasios compactos" },
  { name: "GymFort 500", capacity: "Hasta 500 socios activos", price: "1,590", tag: "Operación en crecimiento" },
  { name: "GymFort 1000", capacity: "Hasta 1,000 socios activos", price: "2,190", tag: "Gimnasios consolidados" },
  { name: "GymFort 2000", capacity: "Hasta 2,000 socios activos", price: "2,990", tag: "Operación de alto volumen" },
];

const includedFeatures = [
  "Todas las funcionalidades actuales de GymFort",
  "Usuarios de personal sin costo adicional",
  "Soporte técnico estándar y actualizaciones",
  "Registros históricos sin consumir capacidad",
];

const implementation = [
  ["GymFort 150", "$2,500"],
  ["GymFort 500", "$3,500"],
  ["GymFort 1000", "$5,000"],
  ["GymFort 2000", "$7,500"],
];

const pricingFaq = [
  ["¿Qué se considera un socio activo?", "Un cliente con una membresía vigente en la sucursal. Prospectos, empleados, históricos y clientes sin membresía vigente no cuentan para determinar el plan."],
  ["¿Cómo se cobra si tengo varias sucursales?", "Cada sucursal se licencia según sus propios socios activos. El propietario puede administrar todas sus sedes desde la misma cuenta, pero cada ubicación se factura de forma independiente."],
  ["¿Los planes bloquean funcionalidades?", "No. Los planes cambian por capacidad, no por módulos. Las funcionalidades actuales de GymFort están disponibles en todos los niveles."],
  ["¿Qué pasa si supero el límite de mi plan?", "GymFort no busca frenar el crecimiento de tu gimnasio. Revisamos la capacidad y, cuando el crecimiento es sostenido, la sucursal pasa al siguiente nivel de servicio."],
  ["¿La implementación se cobra una sola vez?", "Sí. Cubre la puesta en marcha estándar, configuración inicial, migración base y capacitación. Migraciones extraordinarias o desarrollos específicos se cotizan por separado."],
  ["¿Cobran comisión sobre mis ventas?", "No. GymFort no cobra una comisión adicional sobre las ventas registradas por tu gimnasio. Los costos de proveedores de pago externos, cuando se utilicen, son independientes."],
];

export default function Home() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setSession(getStoredSession());
      setReady(true);
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);

  const destination = session ? getPostLoginPath(session.user) : "/login";

  function logout() {
    clearSession();
    setSession(null);
  }

  return <main className="landing-page">
    <header className="landing-nav">
      <Link className="landing-logo" href="/" aria-label="GymFort, inicio"><Image src="/gymfort_web_assets/gymfort-logo-navbar-96px.png" alt="GymFort" width={216} height={40} priority /></Link>
      <nav aria-label="Navegación principal"><a href="#solucion">Solución</a><a href="#funciones">Funciones</a><a href="#como-funciona">Cómo funciona</a><a href="#precios">Precios</a><Link href="/support">Soporte</Link><a href="#contacto">Contacto</a></nav>
      <div className="landing-nav-actions">{ready && session ? <button onClick={logout}>Salir</button> : null}<Link className="landing-login" href={destination}>{session ? "Ir a mi panel" : "Iniciar sesión"}</Link></div>
    </header>

    <section className="landing-hero" id="solucion">
      <div className="landing-hero-copy"><p className="landing-eyebrow"><span /> Gestión inteligente para gimnasios</p><h1>Todo tu gimnasio.<br /><em>Un solo sistema.</em></h1><p className="landing-lead">GymFort centraliza miembros, accesos, ventas, reservas, inventario y reportes para que tu equipo opere con claridad y tú puedas enfocarte en crecer.</p><div className="landing-hero-actions"><Link className="landing-primary-cta" href={destination}>{session ? "Continuar al panel" : "Acceder a la plataforma"}<span>→</span></Link><a className="landing-secondary-cta" href="#precios">Ver precios</a></div><div className="landing-trust"><span>✓ Información centralizada</span><span>✓ Acceso desde la nube</span><span>✓ Roles y permisos</span></div></div>
      <div className="product-preview" aria-label="Vista del panel de GymFort"><div className="preview-glow" /><div className="preview-window"><aside><Image src="/gymfort_web_assets/gymfort-isotipo-64.png" alt="" width={32} height={32} /><b>GymFort</b>{["Dashboard","Miembros","Accesos","Ventas","Reservas","Inventario","Reportes"].map((item, index) => <span className={index === 0 ? "active" : ""} key={item}><i />{item}</span>)}</aside><section><div className="preview-top"><div><small>Resumen general</small><h2>Dashboard</h2></div><b>Admin ⌄</b></div><div className="preview-metrics"><article><span>Miembros activos</span><strong>1,248</strong><small>↗ 12%</small></article><article><span>Check-ins hoy</span><strong>342</strong><small>↗ 8%</small></article><article><span>Ingresos del mes</span><strong>$245,750</strong><small>↗ 15%</small></article><article><span>Nuevas altas</span><strong>98</strong><small>↗ 9%</small></article></div><div className="preview-chart"><div><span>Actividad mensual</span><b>Operación en crecimiento</b></div><svg viewBox="0 0 500 110" role="img" aria-label="Gráfica ascendente"><defs><linearGradient id="chartFill" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="#27dec4" stopOpacity=".3"/><stop offset="1" stopColor="#27dec4" stopOpacity="0"/></linearGradient></defs><path d="M0 96 L55 76 L105 84 L160 48 L215 58 L275 30 L330 42 L390 18 L445 27 L500 4 L500 110 L0 110Z" fill="url(#chartFill)"/><path d="M0 96 L55 76 L105 84 L160 48 L215 58 L275 30 L330 42 L390 18 L445 27 L500 4" fill="none" stroke="#27dec4" strokeWidth="4"/></svg></div></section></div></div>
    </section>

    <section className="landing-proof" aria-label="Beneficios de GymFort"><div><strong>Seguro</strong><span>Roles, permisos y trazabilidad.</span></div><div><strong>En la nube</strong><span>Opera desde cualquier dispositivo.</span></div><div><strong>Todo en uno</strong><span>Menos herramientas desconectadas.</span></div><div><strong>Hecho para crecer</strong><span>Desde una sede hasta múltiples sucursales.</span></div></section>
    <section className="landing-brand-banner" aria-label="GymFort, una plataforma conectada"><div><p>Centralización y conexión</p><h2>Una identidad fuerte para una operación conectada.</h2><span>Seguridad, control y crecimiento sobre una sola plataforma.</span></div></section>
    <section className="landing-section" id="funciones"><div className="landing-section-heading"><p>Una operación conectada</p><h2>Lo que necesitas para administrar mejor</h2><span>GymFort reúne las áreas críticas de tu gimnasio y ayuda a tu equipo a trabajar con una sola fuente de información.</span></div><div className="feature-grid">{features.map(([icon,title,text]) => <article key={title}><span>{icon}</span><h3>{title}</h3><p>{text}</p></article>)}</div></section>
    <section className="landing-steps" id="como-funciona"><div><p>Simple desde el primer día</p><h2>De la configuración a una operación más fuerte.</h2><span>La plataforma acompaña el flujo real de tu negocio sin agregar complejidad innecesaria.</span></div><ol>{steps.map(([title,text], index) => <li key={title}><b>0{index + 1}</b><section><h3>{title}</h3><p>{text}</p></section></li>)}</ol></section>

    <section className={styles.pricingSection} id="precios">
      <div className={styles.sectionHeader}>
        <p className={styles.sectionEyebrow}>Precios simples y transparentes</p>
        <h2>El mismo GymFort.<br />Elige sólo el tamaño de tu gimnasio.</h2>
        <span>Sin módulos esenciales bloqueados ni cobros por cada miembro de tu equipo. El precio de cada sucursal depende únicamente de su número de socios activos.</span>
      </div>
      <div className={styles.pricingNotice}>
        <span>✓ Todas las funcionalidades actuales</span>
        <span>✓ Staff sin costo adicional</span>
        <span>✓ Sin comisión GymFort sobre ventas</span>
        <span>✓ Soporte y actualizaciones incluidos</span>
      </div>
      <div className={styles.pricingGrid}>
        {plans.map((plan) => <article className={styles.pricingCard} key={plan.name}>
          <span className={styles.planTag}>{plan.tag}</span>
          <h3 className={styles.planName}>{plan.name}</h3>
          <p className={styles.planCapacity}>{plan.capacity}</p>
          <div className={styles.priceRow}><span className={styles.priceCurrency}>$</span><strong className={styles.priceAmount}>{plan.price}</strong><span className={styles.pricePeriod}>MXN / mes</span></div>
          <p className={styles.taxNote}>+ IVA · precio por sucursal</p>
          <hr className={styles.planDivider} />
          <ul className={styles.planFeatures}>{includedFeatures.map((feature) => <li key={feature}>{feature}</li>)}</ul>
          <a className={styles.pricingCta} href={`mailto:contacto@gymfort.mx?subject=Quiero%20contratar%20${encodeURIComponent(plan.name)}`}>Solicitar información <span>→</span></a>
        </article>)}
      </div>

      <article className={styles.enterpriseCard}>
        <div>
          <span className={styles.enterpriseBadge}>GymFort Enterprise</span>
          <h3>Para operaciones que necesitan ir más allá.</h3>
          <p>Diseñado para sucursales con más de 2,000 socios activos o grupos con requerimientos especiales de integración, soporte, infraestructura o despliegue.</p>
        </div>
        <div className={styles.enterpriseSide}>
          <strong>Cotización personalizada</strong>
          <span>La operación multi-sucursal no obliga a contratar Enterprise: cada sede puede usar el plan que corresponda a su tamaño.</span>
          <a className={styles.pricingCta} href="mailto:contacto@gymfort.mx?subject=GymFort%20Enterprise">Hablar con GymFort <span>→</span></a>
        </div>
      </article>
    </section>

    <section className={styles.pricingDetails} aria-label="Cómo funciona el precio de GymFort">
      <div className={styles.sectionHeader}>
        <p className={styles.sectionEyebrow}>Una regla fácil de entender</p>
        <h2>Crece sin complicar tu facturación.</h2>
        <span>Una cuenta puede administrar múltiples gimnasios. Cada sucursal conserva su operación independiente y se licencia de acuerdo con su propia capacidad.</span>
      </div>
      <div className={styles.detailsGrid}>
        <article className={styles.detailCard}><h3>Socios activos, no registros históricos</h3><p>Para determinar el plan contamos a los clientes con <strong>membresía vigente</strong> en la sucursal. Tus registros históricos, prospectos y clientes inactivos permanecen en GymFort sin elevar artificialmente tu tarifa.</p></article>
        <article className={styles.detailCard}><h3>Una licencia por sucursal</h3><p>Si administras dos, tres o más ubicaciones, cada una utiliza el plan correspondiente a sus socios activos. <strong>El propietario mantiene una sola cuenta de administración</strong> para operar sus sucursales.</p></article>
        <article className={styles.detailCard}><h3>Sin castigar el crecimiento</h3><p>Superar ocasionalmente un límite no detiene tu operación. Cuando el crecimiento se vuelve sostenido, ajustamos la sucursal al siguiente nivel para que el sistema siga acompañando su escala.</p></article>
        <article className={styles.detailCard}><h3>Costos variables separados</h3><p>Hardware, instalaciones físicas, desarrollos específicos y consumos de proveedores externos como SMS o WhatsApp se cotizan por separado cuando apliquen. <strong>GymFort no cobra comisión adicional sobre tus ventas.</strong></p></article>
      </div>

      <div className={styles.implementation}>
        <div className={styles.implementationHeader}>
          <div><p>Puesta en marcha</p><h3>Implementación inicial por sucursal</h3></div>
          <span>Pago único que cubre configuración estándar, migración base, preparación operativa y capacitación inicial. Proyectos especiales se cotizan según alcance.</span>
        </div>
        <div className={styles.implementationGrid}>
          {implementation.map(([plan, price]) => <div className={styles.implementationItem} key={plan}><b>{plan}</b><strong>{price}</strong><small>MXN + IVA · una sola vez</small></div>)}
        </div>
      </div>
    </section>

    <section className={styles.faqSection} aria-label="Preguntas frecuentes sobre precios">
      <div className={styles.sectionHeader}>
        <p className={styles.sectionEyebrow}>Preguntas frecuentes</p>
        <h2>Sin letras pequeñas en la forma de cobrar.</h2>
        <span>La estructura está diseñada para que sepas qué pagas hoy y cómo cambia tu costo conforme crece tu operación.</span>
      </div>
      <div className={styles.faqGrid}>{pricingFaq.map(([question, answer]) => <article className={styles.faqItem} key={question}><h3>{question}</h3><p>{answer}</p></article>)}</div>
    </section>

    <section className="landing-contact" id="contacto"><div><p>¿Quieres conocer GymFort?</p><h2>Conversemos sobre la operación de tu gimnasio.</h2><span>Cuéntanos cuántos socios activos y sucursales tienes y te ayudaremos a identificar el plan adecuado para cada ubicación.</span></div><a href="mailto:contacto@gymfort.mx?subject=Quiero%20conocer%20GymFort">contacto@gymfort.mx <span>→</span></a></section>
    <footer className="landing-footer"><div><Image src="/gymfort_web_assets/gymfort-logo-navbar-96px.png" alt="GymFort" width={194} height={36} /><p>Software de gestión para gimnasios y centros deportivos.</p></div><nav><a href="#precios">Precios</a><Link href="/privacy-policy">Privacidad</Link><Link href="/terms-of-service">Términos de servicio</Link><Link href="/support">Soporte</Link><a href="mailto:contacto@gymfort.mx">Contacto</a></nav><small>© {new Date().getFullYear()} GymFort. Todos los derechos reservados.</small></footer>
  </main>;
}
