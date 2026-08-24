"use client";

import Image from "next/image";
import Link from "next/link";
import { type FormEvent, useState } from "react";
import { acceptUserInvitation } from "@/lib/api";

export default function AcceptInvitationPage() {
	const [token, setToken] = useState(() =>
		typeof window === "undefined"
			? ""
			: new URLSearchParams(window.location.search).get("token") ?? "");
	const [password, setPassword] = useState("");
	const [confirmation, setConfirmation] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [saving, setSaving] = useState(false);
	const [completed, setCompleted] = useState(false);

	async function submit(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		setError(null);
		if (!token) {
			setError("El enlace de invitación está incompleto.");
			return;
		}
		if (password !== confirmation) {
			setError("Las contraseñas no coinciden.");
			return;
		}
		setSaving(true);
		try {
			await acceptUserInvitation(token, password);
			setCompleted(true);
			setToken("");
			setPassword("");
			setConfirmation("");
			window.history.replaceState({}, "", "/accept-invitation");
		} catch (exception) {
			setError(exception instanceof Error ? exception.message : "No fue posible activar la cuenta.");
		} finally {
			setSaving(false);
		}
	}

	return (
		<main className="login-screen">
			<section className="login-card invitation-accept-card">
				<div className="login-hero">
					<p>Invitación segura</p>
					<h1>Activa tu cuenta de gimnasio.</h1>
					<span>Define tu propia contraseña. El enlace sólo puede utilizarse una vez y no guardamos la contraseña en texto claro.</span>
				</div>
				<section className="login-form-panel">
					<Image className="login-form-logo" src="/gymfort_web_assets/gymfort-logo-navbar-96px.png" alt="GymFort" width={178} height={33} priority />
					{completed ? (
						<div className="grid gap-4">
							<h2>Cuenta activada</h2>
							<p>Ya puedes iniciar sesión con el correo al que se envió la invitación.</p>
							<Link className="form-primary-button text-center" href="/login">Ir a iniciar sesión</Link>
						</div>
					) : (
						<form onSubmit={submit}>
							<h2>Crear contraseña</h2>
							<p>Usa 12 o más caracteres con mayúscula, minúscula, número y símbolo.</p>
							<label>
								Nueva contraseña
								<input autoComplete="new-password" maxLength={120} minLength={12} onChange={(event) => setPassword(event.target.value)} required type="password" value={password} />
							</label>
							<label>
								Confirmar contraseña
								<input autoComplete="new-password" maxLength={120} minLength={12} onChange={(event) => setConfirmation(event.target.value)} required type="password" value={confirmation} />
							</label>
							{error ? <p className="login-error">{error}</p> : null}
							<button disabled={saving} type="submit">{saving ? "Activando..." : "Activar mi cuenta"}</button>
						</form>
					)}
				</section>
			</section>
		</main>
	);
}
