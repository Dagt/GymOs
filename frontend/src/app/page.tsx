"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useState } from "react";
import { clearSession, getStoredSession, type StoredSession } from "@/lib/api";
import { getPostLoginPath } from "@/lib/session-routing";

const features = [
  ["01", "Miembros y membresías", "Expedientes, planes, renovaciones y seguimiento en un solo lugar."],
  ["02", "Accesos y autoservicio", "Controla entradas y ofrece una experiencia ágil desde el kiosco."],
  ["03", "Ventas e inventario", "Opera caja, productos, existencias y movimientos con trazabilidad."],
  ["04", "Reservas y entrenamiento", "Coordina espacios, clases, coaches y rutinas desde la misma plataforma."],
  ["05", "Reportes para decidir", "Consulta indicadores operativos y comerciales para actuar a tiempo."],
  ["06", "Operación multi-sucursal", "Separa equipos, permisos y datos sin perder una visión centralizada."],
];

const steps = [["Configura", "Registra tu gimnasio, sucursales, equipo y catálogo."], ["Opera", "Gestiona la actividad diaria desde cualquier dispositivo."], ["Crece", "Automatiza tareas y toma decisiones con información ordenada."]];

export default function Home() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [ready, setReady] = useState(false);
  useEffect(() => { const timer = window.setTimeout(() => { setSession(getStoredSession()); setReady(true); }, 0); return () => window.clearTimeout(timer); }, []);
  const destination = session ? getPostLoginPath(session.user) : "/login";
  function logout() { clearSession(); setSession(null); }

  return <main className="landing-page">
    <header className="landing-nav">
      <Link className="landing-logo" href="/" aria-label="GymFort, inicio"><Image src="/gymfort_web_assets/gymfort-logo-navbar-96px.png" alt="GymFort" width={216} height={40} priority /></Link>
      <nav aria-label="Navegación principal"><a href="#solucion">Solución</a><a href="#funciones">Funciones</a><a href="#como-funciona">Cómo funciona</a><Link href="/support">Soporte</Link><a href="#contacto">Contacto</a></nav>
      <div className="landing-nav-actions">{ready && session ? <button onClick={logout}>Salir</button> : null}<Link className="landing-login" href={destination}>{session ? "Ir a mi panel" : "Iniciar sesión"}</Link></div>
    </header>

    <section className="landing-hero" id="solucion">
      <div className="landing-hero-copy"><p className="landing-eyebrow"><span /> Gestión inteligente para gimnasios</p><h1>Todo tu gimnasio.<br /><em>Un solo sistema.</em></h1><p className="landing-lead">GymFort centraliza miembros, accesos, ventas, reservas, inventario y reportes para que tu equipo opere con claridad y tú puedas enfocarte en crecer.</p><div className="landing-hero-actions"><Link className="landing-primary-cta" href={destination}>{session ? "Continuar al panel" : "Acceder a la plataforma"}<span>→</span></Link><a className="landing-secondary-cta" href="#contacto">Hablar con nosotros</a></div><div className="landing-trust"><span>✓ Información centralizada</span><span>✓ Acceso desde la nube</span><span>✓ Roles y permisos</span></div></div>
      <div className="product-preview" aria-label="Vista del panel de GymFort"><div className="preview-glow" /><div className="preview-window"><aside><Image src="/gymfort_web_assets/gymfort-isotipo-64.png" alt="" width={32} height={32} /><b>GymFort</b>{['Dashboard','Miembros','Accesos','Ventas','Reservas','Inventario','Reportes'].map((item, index) => <span className={index === 0 ? 'active' : ''} key={item}><i />{item}</span>)}</aside><section><div className="preview-top"><div><small>Resumen general</small><h2>Dashboard</h2></div><b>Admin ⌄</b></div><div className="preview-metrics"><article><span>Miembros activos</span><strong>1,248</strong><small>↗ 12%</small></article><article><span>Check-ins hoy</span><strong>342</strong><small>↗ 8%</small></article><article><span>Ingresos del mes</span><strong>$245,750</strong><small>↗ 15%</small></article><article><span>Nuevas altas</span><strong>98</strong><small>↗ 9%</small></article></div><div className="preview-chart"><div><span>Actividad mensual</span><b>Operación en crecimiento</b></div><svg viewBox="0 0 500 110" role="img" aria-label="Gráfica ascendente"><defs><linearGradient id="chartFill" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="#27dec4" stopOpacity=".3"/><stop offset="1" stopColor="#27dec4" stopOpacity="0"/></linearGradient></defs><path d="M0 96 L55 76 L105 84 L160 48 L215 58 L275 30 L330 42 L390 18 L445 27 L500 4 L500 110 L0 110Z" fill="url(#chartFill)"/><path d="M0 96 L55 76 L105 84 L160 48 L215 58 L275 30 L330 42 L390 18 L445 27 L500 4" fill="none" stroke="#27dec4" strokeWidth="4"/></svg></div></section></div></div>
    </section>

    <section className="landing-proof" aria-label="Beneficios de GymFort"><div><strong>Seguro</strong><span>Roles, permisos y trazabilidad.</span></div><div><strong>En la nube</strong><span>Opera desde cualquier dispositivo.</span></div><div><strong>Todo en uno</strong><span>Menos herramientas desconectadas.</span></div><div><strong>Hecho para crecer</strong><span>Desde una sede hasta múltiples sucursales.</span></div></section>
    <section className="landing-brand-banner" aria-label="GymFort, una plataforma conectada"><div><p>Centralización y conexión</p><h2>Una identidad fuerte para una operación conectada.</h2><span>Seguridad, control y crecimiento sobre una sola plataforma.</span></div></section>
    <section className="landing-section" id="funciones"><div className="landing-section-heading"><p>Una operación conectada</p><h2>Lo que necesitas para administrar mejor</h2><span>GymFort reúne las áreas críticas de tu gimnasio y ayuda a tu equipo a trabajar con una sola fuente de información.</span></div><div className="feature-grid">{features.map(([icon,title,text]) => <article key={title}><span>{icon}</span><h3>{title}</h3><p>{text}</p></article>)}</div></section>
    <section className="landing-steps" id="como-funciona"><div><p>Simple desde el primer día</p><h2>De la configuración a una operación más fuerte.</h2><span>La plataforma acompaña el flujo real de tu negocio sin agregar complejidad innecesaria.</span></div><ol>{steps.map(([title,text], index) => <li key={title}><b>0{index + 1}</b><section><h3>{title}</h3><p>{text}</p></section></li>)}</ol></section>
    <section className="landing-contact" id="contacto"><div><p>¿Quieres conocer GymFort?</p><h2>Conversemos sobre la operación de tu gimnasio.</h2><span>Cuéntanos qué necesitas y te ayudaremos a evaluar si GymFort es adecuado para tu equipo.</span></div><a href="mailto:contacto@gymfort.mx?subject=Quiero%20conocer%20GymFort">contacto@gymfort.mx <span>→</span></a></section>
    <footer className="landing-footer"><div><Image src="/gymfort_web_assets/gymfort-logo-navbar-96px.png" alt="GymFort" width={194} height={36} /><p>Software de gestión para gimnasios y centros deportivos.</p></div><nav><Link href="/privacy-policy">Privacidad</Link><Link href="/terms-of-service">Términos de servicio</Link><Link href="/support">Soporte</Link><a href="mailto:contacto@gymfort.mx">Contacto</a></nav><small>© {new Date().getFullYear()} GymFort. Todos los derechos reservados.</small></footer>
  </main>;
}
