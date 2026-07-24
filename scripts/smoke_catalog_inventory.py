#!/usr/bin/env python3
"""Smoke test reproducible para catálogo, imágenes, ventas e inventario.

Variables requeridas:
  GYMOS_SMOKE_USERNAME
  GYMOS_SMOKE_PASSWORD

Variables opcionales:
  GYMOS_BASE_URL=http://localhost:8080/api/v1

El script crea datos temporales, valida carga/sustitución/eliminación de imagen,
registra inventario inicial, vende, verifica descuento y procesa dos devoluciones
por línea: una reposicionable y otra no reposicionable. Finalmente archiva el
producto temporal.
"""

from __future__ import annotations

import base64
import json
import os
import time
import uuid
from dataclasses import dataclass
from typing import Any
from urllib.error import HTTPError
from urllib.request import Request, urlopen


PNG_ONE = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
)
PNG_TWO = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8zwAAAgEBAV4n0tEAAAAASUVORK5CYII="
)


@dataclass
class Client:
    base_url: str
    token: str | None = None

    def request(
        self,
        method: str,
        path: str,
        payload: dict[str, Any] | None = None,
        headers: dict[str, str] | None = None,
        body: bytes | None = None,
    ) -> Any:
        request_headers = {"Accept": "application/json"}
        if self.token:
            request_headers["Authorization"] = f"Bearer {self.token}"
        if headers:
            request_headers.update(headers)
        if payload is not None:
            body = json.dumps(payload).encode("utf-8")
            request_headers["Content-Type"] = "application/json"
        request = Request(
            f"{self.base_url.rstrip('/')}/{path.lstrip('/')}",
            data=body,
            headers=request_headers,
            method=method,
        )
        try:
            with urlopen(request, timeout=30) as response:
                content = response.read()
                return json.loads(content.decode("utf-8")) if content else None
        except HTTPError as exception:
            detail = exception.read().decode("utf-8", errors="replace")
            raise RuntimeError(
                f"{method} {path} devolvió HTTP {exception.code}: {detail}"
            ) from exception

    def multipart_image(self, product_id: str, filename: str, content: bytes) -> Any:
        boundary = f"----GymOSSmoke{uuid.uuid4().hex}"
        body = (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
            "Content-Type: image/png\r\n\r\n"
        ).encode("utf-8") + content + f"\r\n--{boundary}--\r\n".encode("utf-8")
        return self.request(
            "POST",
            f"catalog/products/{product_id}/image",
            headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
            body=body,
        )


def find_access_token(value: Any) -> str | None:
    if isinstance(value, dict):
        for key, item in value.items():
            if key.lower() in {"accesstoken", "access_token"} and isinstance(item, str):
                return item
        for item in value.values():
            token = find_access_token(item)
            if token:
                return token
    if isinstance(value, list):
        for item in value:
            token = find_access_token(item)
            if token:
                return token
    return None


def find_sale_id(value: Any) -> str | None:
    if isinstance(value, dict):
        sale = value.get("sale")
        if isinstance(sale, dict) and isinstance(sale.get("id"), str):
            return sale["id"]
        if isinstance(value.get("id"), str) and "saleNumber" in value:
            return value["id"]
        for item in value.values():
            result = find_sale_id(item)
            if result:
                return result
    if isinstance(value, list):
        for item in value:
            result = find_sale_id(item)
            if result:
                return result
    return None


def find_sale_item_id(value: Any) -> str | None:
    if not isinstance(value, dict):
        return None
    items = value.get("items")
    if not isinstance(items, list) or not items:
        return None
    first = items[0]
    return first.get("id") if isinstance(first, dict) and isinstance(first.get("id"), str) else None


def wait_for_stock(client: Client, product_id: str, expected: int) -> None:
    last_value: int | None = None
    for _ in range(40):
        detail = client.request("GET", f"inventory/{product_id}")
        last_value = int(detail["quantityOnHand"])
        if last_value == expected:
            return
        time.sleep(0.25)
    raise RuntimeError(
        f"Existencia inesperada para {product_id}: se esperaba {expected}, se obtuvo {last_value}."
    )


def main() -> None:
    username = os.environ.get("GYMOS_SMOKE_USERNAME")
    password = os.environ.get("GYMOS_SMOKE_PASSWORD")
    if not username or not password:
        raise SystemExit(
            "Define GYMOS_SMOKE_USERNAME y GYMOS_SMOKE_PASSWORD con un usuario "
            "que tenga permisos de catálogo, imágenes, inventario, venta y reembolso."
        )

    client = Client(os.environ.get("GYMOS_BASE_URL", "http://localhost:8080/api/v1"))
    login = client.request(
        "POST", "auth/login", {"username": username, "password": password}
    )
    client.token = find_access_token(login)
    if not client.token:
        raise RuntimeError("La respuesta de login no contiene accessToken.")

    unique = uuid.uuid4().hex[:10].upper()
    product = client.request(
        "POST",
        "catalog/products",
        {
            "sku": f"SMOKE-{unique}",
            "name": "Producto smoke catálogo inventario",
            "description": "Dato temporal creado por smoke_catalog_inventory.py",
            "productType": "PHYSICAL",
            "commercialSubtype": "GENERAL_PRODUCT",
            "priceCents": 1000,
            "currency": "MXN",
            "taxable": True,
            "status": "ACTIVE",
            "barcode": None,
            "brand": "GymOS Smoke",
            "presentation": "Unidad",
            "unitOfMeasure": "PIECE",
            "inventoryTracked": True,
            "displayOrder": 999,
            "supplierName": None,
            "referenceCostCents": None,
            "promotionalPriceCents": None,
            "promotionStartsAt": None,
            "promotionEndsAt": None,
            "categoryId": None,
            "tags": ["smoke"],
            "version": None,
        },
    )
    product_id = product["id"]

    try:
        first_image = client.multipart_image(product_id, "smoke-one.png", PNG_ONE)
        second_image = client.multipart_image(product_id, "smoke-two.png", PNG_TWO)
        if not first_image.get("imageObjectKey"):
            raise RuntimeError("La primera carga no devolvió imageObjectKey.")
        if first_image["imageObjectKey"] == second_image.get("imageObjectKey"):
            raise RuntimeError("La sustitución de imagen no generó una nueva clave.")
        removed = client.request("DELETE", f"catalog/products/{product_id}/image")
        if removed.get("imageObjectKey") is not None:
            raise RuntimeError("La eliminación de imagen no limpió imageObjectKey.")

        initial = client.request(
            "POST",
            "inventory/movements",
            {
                "productId": product_id,
                "movementType": "INITIAL",
                "quantity": 5,
                "unitCostCents": 500,
                "referenceType": "SMOKE",
                "referenceId": None,
                "reason": "Existencia inicial del smoke test",
            },
        )
        if initial["quantityOnHand"] != 5:
            raise RuntimeError("El movimiento inicial no dejó cinco unidades.")

        sale = client.request(
            "POST",
            "sales",
            {
                "clientId": None,
                "items": [
                    {"productId": product_id, "quantity": 2, "discountCents": 0}
                ],
                "discountCents": 0,
                "notes": "Smoke integrado catálogo-inventario",
                "payment": {
                    "method": "CASH",
                    "amountCents": 2000,
                    "reference": f"SMOKE-{unique}",
                    "receiptObjectKey": None,
                    "validateNow": True,
                    "notes": "Pago smoke",
                },
            },
            headers={"Idempotency-Key": f"sale-smoke-{uuid.uuid4().hex}"},
        )
        sale_id = find_sale_id(sale)
        sale_item_id = find_sale_item_id(sale)
        if not sale_id or not sale_item_id:
            raise RuntimeError("No fue posible resolver la venta o su línea en la respuesta.")
        wait_for_stock(client, product_id, 3)

        first_refund = client.request(
            "POST",
            f"sales/{sale_id}/refunds",
            {
                "paymentId": None,
                "amountCents": 1000,
                "reason": "Devolución reposicionable del smoke test",
                "idempotencyKey": f"refund-smoke-{uuid.uuid4().hex}",
                "items": [
                    {"saleItemId": sale_item_id, "quantity": 1, "restock": True}
                ],
            },
        )
        wait_for_stock(client, product_id, 4)
        if first_refund["items"][0].get("refundedQuantity") != 1:
            raise RuntimeError("La primera devolución no actualizó refundedQuantity.")

        second_refund = client.request(
            "POST",
            f"sales/{sale_id}/refunds",
            {
                "paymentId": None,
                "amountCents": 1000,
                "reason": "Devolución dañada no reposicionable del smoke test",
                "idempotencyKey": f"refund-smoke-{uuid.uuid4().hex}",
                "items": [
                    {"saleItemId": sale_item_id, "quantity": 1, "restock": False}
                ],
            },
        )
        wait_for_stock(client, product_id, 4)
        if second_refund["items"][0].get("refundedQuantity") != 2:
            raise RuntimeError("La segunda devolución no acumuló refundedQuantity.")

        print(
            json.dumps(
                {
                    "status": "OK",
                    "productId": product_id,
                    "saleId": sale_id,
                    "refundedQuantity": 2,
                    "finalStock": 4,
                },
                ensure_ascii=False,
            )
        )
    finally:
        try:
            client.request("DELETE", f"catalog/products/{product_id}")
        except RuntimeError as exception:
            print(f"Advertencia: no fue posible archivar el producto temporal: {exception}")


if __name__ == "__main__":
    main()
