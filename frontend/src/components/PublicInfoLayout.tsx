import Image from "next/image";
import Link from "next/link";
import type { ReactNode } from "react";

type PublicInfoLayoutProps = {
  children: ReactNode;
  description: string;
  eyebrow: string;
  title: string;
  updated?: string;
};

export function PublicInfoLayout({ children, description, eyebrow, title, updated }: PublicInfoLayoutProps) {
  return (
    <main className="public-info-page">
      <header className="public-info-nav">
        <Link href="/" aria-label="GymFort, inicio">
          <Image
            src="/gymfort_web_assets/gymfort-logo-navbar-96px.png"
            alt="GymFort"
            width={194}
            height={36}
            priority
          />
        </Link>
        <nav aria-label="Navegación de información">
          <Link href="/privacy-policy">Privacidad</Link>
          <Link href="/terms-of-service">Términos</Link>
          <Link href="/support">Soporte</Link>
          <Link className="public-info-login" href="/login">Iniciar sesión</Link>
        </nav>
      </header>

      <section className="public-info-hero">
        <p>{eyebrow}</p>
        <h1>{title}</h1>
        <span>{description}</span>
        {updated ? <small>Última actualización: {updated}</small> : null}
      </section>

      <article className="public-info-article">
        {children}
      </article>

      <footer className="public-info-footer">
        <div>
          <Image src="/gymfort_web_assets/gymfort-logo-navbar-96px.png" alt="GymFort" width={172} height={32} />
          <p>Todo tu gimnasio. Un solo sistema.</p>
        </div>
        <nav aria-label="Enlaces legales">
          <Link href="/privacy-policy">Aviso de privacidad</Link>
          <Link href="/terms-of-service">Términos de servicio</Link>
          <Link href="/support">Centro de soporte</Link>
          <a href="mailto:contacto@gymfort.mx">contacto@gymfort.mx</a>
        </nav>
        <small>© {new Date().getFullYear()} GymFort. Todos los derechos reservados.</small>
      </footer>
    </main>
  );
}
