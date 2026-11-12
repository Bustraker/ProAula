const { test, expect } = require("@playwright/test");

test("public trip map loads Cartagena neighborhood and locality layers on demand", async ({ page }) => {
  test.setTimeout(60_000);
  const cartographyRequests = [];
  page.on("request", request => {
    if (request.url().includes("/data/cartografia/")) {
      cartographyRequests.push(request.url());
    }
  });

  await page.goto("/viajar_public");
  await expect(page.locator("#map .leaflet-control-layers")).toBeVisible();
  expect(cartographyRequests).toHaveLength(0);

  await page.locator(".leaflet-control-layers").hover();
  const overlays = page.locator(".leaflet-control-layers-overlays");
  const barrios = overlays.locator("label").filter({ hasText: "Barrios · límites catastrales" }).locator("input");
  await expect(barrios).toBeVisible();
  await expect(barrios).not.toBeChecked();
  const barriosResponse = page.waitForResponse(response =>
    response.url().includes("/data/cartografia/barrios-cartografia.geojson") && response.status() === 200
  );
  await barrios.check();
  const barriosGeoJson = await (await barriosResponse).json();
  expect(barriosGeoJson.features).toHaveLength(217);
  expect(barriosGeoJson.features.every(feature => feature.properties.nombre)).toBe(true);

  await expect.poll(() => page.locator("#map path.leaflet-interactive").count()).toBeGreaterThan(0);
  const visiblePathIndex = await page.locator("#map path.leaflet-interactive").evaluateAll(paths => {
    const mapBounds = document.querySelector("#map").getBoundingClientRect();
    const panelBounds = document.querySelector(".options-panel").getBoundingClientRect();
    return paths.findIndex(path => {
      const bounds = path.getBoundingClientRect();
      return bounds.width > 2 && bounds.height > 2 &&
        bounds.right > mapBounds.left && bounds.left < mapBounds.right &&
        bounds.bottom > mapBounds.top && bounds.top < mapBounds.bottom &&
        (bounds.right <= panelBounds.left || bounds.left >= panelBounds.right ||
          bounds.bottom <= panelBounds.top || bounds.top >= panelBounds.bottom);
    });
  });
  expect(visiblePathIndex).toBeGreaterThanOrEqual(0);
  await page.locator("#map path.leaflet-interactive").nth(visiblePathIndex).click();

  const popup = page.locator(".leaflet-popup-content");
  await expect(popup).toContainText("Barrio");
  const originButton = popup.locator("[data-set-origin]");
  const neighborhood = await originButton.getAttribute("data-set-origin");
  expect(neighborhood).toBeTruthy();
  await originButton.click();
  await expect(page.locator("#origen")).toHaveValue(neighborhood);

  await page.locator(".leaflet-control-layers").hover();
  const localidades = overlays.locator("label").filter({ hasText: "Localidades · límites catastrales" }).locator("input");
  await expect(localidades).toBeVisible();
  const localidadesResponse = page.waitForResponse(response =>
    response.url().includes("/data/cartografia/localidades-cartografia.geojson") && response.status() === 200
  );
  await localidades.check();
  const localidadesGeoJson = await (await localidadesResponse).json();
  expect(localidadesGeoJson.features).toHaveLength(3);
  expect(localidadesGeoJson.features.every(feature => feature.properties.nombre)).toBe(true);
  await expect.poll(() => page.locator("#map path.leaflet-interactive").count()).toBeGreaterThan(1);
});
