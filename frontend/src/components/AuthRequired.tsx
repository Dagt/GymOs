import Link from "next/link";

export function AuthRequired({
  title = "Inicia sesión para continuar",
  message = "Usa una cuenta autorizada para ver esta sección de GymOS.",
}: {
  title?: string;
  message?: string;
}) {
  return (
    <main className="auth-required-screen">
      <section className="auth-required-card">
        <p className="app-eyebrow">Acceso seguro</p>
        <h1>{title}</h1>
        <p>{message}</p>
        <Link className="app-button" href="/login">
          Iniciar sesión
        </Link>
      </section>
    </main>
  );
}
