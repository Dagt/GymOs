import type { Page } from "@playwright/test";

export async function mockStaffSession(page: Page, accessToken: string, user: object) {
  await page.addInitScript(({ accessToken, user }) => {
    sessionStorage.setItem("gymfort.session.slot", "00000000-0000-4000-8000-000000000001");
    const fetchNetwork = window.fetch.bind(window);
    window.fetch = (input, init) => {
      const url = new URL(input instanceof Request ? input.url : String(input), location.href);
      if (url.pathname === "/api/v1/auth/refresh") {
        return Promise.resolve(Response.json({ accessToken, user, tokenType: "Bearer", expiresIn: 900 }));
      }
      if (url.pathname === "/api/v1/auth/logout") {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return fetchNetwork(input, init);
    };
  }, { accessToken, user });
}
