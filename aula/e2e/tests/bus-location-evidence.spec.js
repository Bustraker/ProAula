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
    fullPage: true,
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
  await page.locator("#btnPlanificar").click();
  const tramo = page.locator(".tramo").filter({ hasText: route.nombre }).first();
  await expect(tramo).toBeVisible();
  await tramo.click();
  await expect(page.locator("#rutaDetalle")).toBeVisible();
  await expect(page.locator("#llegadasRutaPanel")).toBeVisible();
}

if (evidenceEnabled) {
  test("captures the real route with no GPS reports from the running app", async ({ page }) => {
    await selectAvailableRoute(page);
    const fallbackStatus = page.locator(".arrival-card__status");
    await expect(fallbackStatus).toContainText("recorrido seleccionado sigue visible");
    await expect(page.locator(".arrival-bus-marker")).toHaveCount(0);
    await expect(page.locator("#map .leaflet-overlay-pane path").first()).toBeVisible();
    await page.locator("#llegadasRutaPanel").scrollIntoViewIfNeeded();
    await saveEvidence(page, "BUS-54_ruta-visible-sin-reportes.png");
  });
}
