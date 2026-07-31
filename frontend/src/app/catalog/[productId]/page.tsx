"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import { catalogUnitLabel } from "@/features/catalog/catalog-units";
import { getStoredSession, type StoredSession } from "@/lib/api";
import { getProductDetail } from "@/lib/api/catalog";
import type { ProductDetail } from "@/lib/api/catalog-types";
import { formatMoney } from "../CatalogHelpers";

export default function CatalogProductDetailPage() {
  const params = useParams<{ productId: string }>();
  const [session, setSession] = useState<StoredSession | null>(null);
  const [detail, setDetail] = useState<ProductDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;

    async function loadDetail() {
      await Promise.resolve();
      if (!active) return;
      const stored = getStoredSession();
      setSession(stored);
      if (!stored) {
        setLoading(false);
        return;
      }
      try {
        const loaded = await getProductDetail(stored.accessToken, params.productId);
        if (active) setDetail(loaded);
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar el producto.");
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void loadDetail();
    return () => {
      active = false;
    };
  }, [params.productId]);

  if (loading) return <LoadingScreen text="Cargando detalle del producto..." />;
  if (!session) return <AuthRequired message="Necesitas iniciar sesión para consultar el producto." title="Inicia sesión" />;
  if (!detail) return <AppShell eyebrow="Catálogo" session={session} title="Producto">{error ? <InlineError message={error} /> : null}</AppShell>;

  const { product, dependencies, history } = detail;
  const canReadCosts = hasPermission(session, "catalog.cost.read");
  return (
    <AppShell eyebrow="Catálogo" session={session} title={product.name}
      subtitle={`${product.sku} · ${product.categoryName ?? "Sin categoría"}`}>
      {error ? <InlineError message={error} /> : null}
      <div className="flex flex-wrap gap-3">
        <Link className="app-button-secondary" href="/catalog">Volver al catálogo</Link>
        {product.inventoryTracked ? <Link className="app-button" href="/inventory">Ver inventario</Link> : null}
      </div>
      <section className="grid gap-6 lg:grid-cols-2">
        <article className="app-card p-5">
          <p className="app-eyebrow">Información comercial</p>
          <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
            <Detail label="Estado" value={product.archived ? "Archivado" : product.status} />
            <Detail label="Tipo" value={product.productType} />
            <Detail label="Subtipo" value={product.commercialSubtype} />
            <Detail label="Precio regular" value={formatMoney(product.priceCents, product.currency)} />
            <Detail label="Precio promocional" value={product.promotionalPriceCents == null ? "Sin promoción" : formatMoney(product.promotionalPriceCents, product.currency)} />
            {canReadCosts ? <Detail label="Costo de referencia" value={product.referenceCostCents == null ? "Sin costo" : formatMoney(product.referenceCostCents, product.currency)} /> : null}
            {canReadCosts ? <Detail label="Proveedor" value={product.supplierName ?? "Sin proveedor"} /> : null}
            <Detail label="Código de barras" value={product.barcode ?? "Sin código"} />
            <Detail label="Marca" value={product.brand ?? "Sin marca"} />
            <Detail label="Presentación" value={product.presentation ?? "Sin presentación"} />
            <Detail label="Unidad" value={catalogUnitLabel(product.unitOfMeasure)} />
            <Detail label="Inventario" value={product.inventoryTracked ? "Controlado" : "No controlado"} />
            <Detail label="Inicio promoción" value={formatDate(product.promotionStartsAt)} />
            <Detail label="Fin promoción" value={formatDate(product.promotionEndsAt)} />
            <Detail label="Etiquetas" value={product.tags.join(", ") || "Sin etiquetas"} />
          </dl>
        </article>
        <article className="app-card p-5">
          <p className="app-eyebrow">Dependencias</p>
          <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
            <Detail label="Líneas vendidas" value={String(dependencies.saleItems)} />
            <Detail label="Movimientos" value={String(dependencies.inventoryMovements)} />
            <Detail label="Sucursales con stock" value={String(dependencies.branchesWithStock)} />
            <Detail label="Planes activos" value={String(dependencies.activeMembershipPlans)} />
            <Detail label="Productos de categoría" value={String(dependencies.activeProductsInCategory)} />
          </dl>
        </article>
      </section>
      <section className="app-card overflow-hidden">
        <div className="border-b border-[#e3e7df] p-4"><p className="app-eyebrow">Historial</p></div>
        {history.length === 0 ? <p className="p-5 text-sm text-[#5d6b63]">No hay eventos registrados.</p> : (
          <div className="divide-y divide-[#e3e7df]">
            {history.map((entry) => (
              <article className="p-4" key={entry.id}>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <strong>{entry.action}</strong>
                  <time className="text-xs text-[#6f7a73]">{new Date(entry.occurredAt).toLocaleString("es-MX")}</time>
                </div>
                {entry.reason ? <pre className="mt-2 overflow-x-auto whitespace-pre-wrap text-xs text-[#5d6b63]">{entry.reason}</pre> : null}
              </article>
            ))}
          </div>
        )}
      </section>
    </AppShell>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return <div><dt className="text-[#6f7a73]">{label}</dt><dd className="font-medium">{value}</dd></div>;
}

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString("es-MX") : "Sin vigencia";
}

function hasPermission(session: StoredSession, permission: string) {
  return session.user.permissions.includes(permission)
    || session.user.permissions.includes("catalog.manage")
    || session.user.permissions.includes("platform.admin");
}
