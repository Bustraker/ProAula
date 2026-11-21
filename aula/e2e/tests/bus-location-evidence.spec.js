const fs = require("node:fs");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const evidenceEnabled = process.env.BUSTRAKER_E2E_BUSLOCATIONEVIDENCE === "true";

async function saveEvidence(page, filename) {
  const directory = process.env.BUS_EVIDENCE_DIR;
  if (!directory) return;
  fs.mkdirSync(directory, { recursive: true });
  await page.screenshot({
    path: path.join(directory, filename),
    fullPage: false,
    animations: "disabled"
  });
}

async function selectAvailableRoute(page) {
  await page.route("https://unpkg.com/leaflet@1.9.4/dist/leaflet.js", async route => {
    const response = await route.fetch();
    await route.fulfill({ response });
  });
  await page.goto("/viajar_public");
  const routes = await page.evaluate(async () => {
    const response = await fetch("/api/rutas-con-coordenadas");
    return response.json();
  });
  const route = routes.find(candidate =>
    candidate.ordenBarriosConfirmado === true
    && Array.isArray(candidate.barriosOrdenados)
    && candidate.barriosOrdenados.length >= 2
  );
  expect(route, "The running application did not return a verified route with ordered neighborhoods").toBeTruthy();

  const barrios = route.barriosOrdenados;
  await page.locator("#origen").fill(barrios[0]);
  await page.locator("#destino").fill(barrios[barrios.length - 1]);
  const roadResponsePromise = page.waitForResponse(async response => {
    if (!response.url().includes("/route/v1/driving/") || !response.ok()) return false;
    const data = await response.json();
    return data.code === "Ok"
      && Array.isArray(data.routes)
      && data.routes.length > 0
      && Array.isArray(data.routes[0].geometry && data.routes[0].geometry.coordinates);
  }, { timeout: 45000 });
  await page.locator("#btnPlanificar").click();
  const tramo = page.locator(".tramo").filter({ hasText: route.nombre }).first();
  await expect(tramo).toBeVisible();
  await tramo.click();
  await expect(page.locator("#rutaDetalle")).toBeVisible();
  const roadResponse = await roadResponsePromise;
  const roadData = await roadResponse.json();
  expect(roadData.routes[0].geometry.coordinates.length)
    .toBeGreaterThan(barrios.length);
  return { barrioCount: barrios.length };
}

if (evidenceEnabled) {
  test("draws road geometry from the live routing service", async ({ page }) => {
    const { barrioCount } = await selectAvailableRoute(page);
    const routePath = page.locator("#map .leaflet-overlay-pane path.leaflet-interactive").first();
    await expect(routePath).toBeVisible();
    await expect.poll(async () => {
      const pathData = await routePath.getAttribute("d");
      return (pathData && pathData.match(/L/g) || []).length;
    }).toBeGreaterThan(barrioCount);
    await page.locator("#llegadasRutaPanel").scrollIntoViewIfNeeded();
    await saveEvidence(page, "BUS-54_ruta-con-geometria-OSRM.png");
  });
}
