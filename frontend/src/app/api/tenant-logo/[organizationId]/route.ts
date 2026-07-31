import { NextRequest, NextResponse } from "next/server";

const API_BASE_URL =
  process.env.GYMOS_INTERNAL_API_BASE_URL
  ?? process.env.NEXT_PUBLIC_API_BASE_URL
  ?? "http://localhost:8080/api/v1";

export async function GET(
  _request: NextRequest,
  context: { params: Promise<{ organizationId: string }> },
) {
  const { organizationId } = await context.params;
  let response: Response;
  try {
    response = await fetch(
      `${API_BASE_URL}/organizations/${encodeURIComponent(organizationId)}/logo`,
      { cache: "no-store" },
    );
  } catch {
    return NextResponse.json(
      { message: "No fue posible consultar el logo del gimnasio." },
      { status: 502 },
    );
  }

  if (!response.ok) {
    return new NextResponse(null, { status: response.status });
  }

  return new NextResponse(await response.arrayBuffer(), {
    status: 200,
    headers: {
      "Cache-Control": response.headers.get("Cache-Control") ?? "public, max-age=31536000, immutable",
      "Content-Type": response.headers.get("Content-Type") ?? "application/octet-stream",
    },
  });
}
