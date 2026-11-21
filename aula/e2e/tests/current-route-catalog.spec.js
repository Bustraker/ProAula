const { test, expect } = require("@playwright/test");

function normalizeName(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .replace(/[^a-zA-Z0-9]+/g, " ")
    .trim()
    .toLowerCase();
}

function selectableRoutes(routes) {
  return routes.filter(route =>
    (route.estado == null || route.estado === "ACTIVA")
    && route.ordenBarriosConfirmado === true
    && Array.isArray(route.barriosOrdenados)
    && route.barriosOrdenados.length >= 2
    && route.barriosOrdenados.every(barrio =>
      typeof barrio === "string" && normalizeName(barrio)
    )
  );
}

async function stubRoadRouting(page, requests) {
  const handler = async route => {
    const url = new URL(route.request().url());
    const encodedWaypoints = decodeURIComponent(url.pathname.split("/").pop());
    const waypoints = encodedWaypoints.split(";").map(pair =>
      pair.split(",").map(Number)
    );
    requests.push({ url: route.request().url(), waypointCount: waypoints.length });

    const geometry = [];
    for (let index = 0; index < waypoints.length - 1; index++) {
      const start = waypoints[index];
      const end = waypoints[index + 1];
      const deltaLng = end[0] - start[0];
      const deltaLat = end[1] - start[1];
      const length = Math.hypot(deltaLng, deltaLat) || 1;
      geometry.push(start, [
        (start[0] + end[0]) / 2 - (deltaLat / length) * 0.0005,
        (start[1] + end[1]) / 2 + (deltaLng / length) * 0.0005
      ]);
    }
    if (waypoints.length) geometry.push(waypoints[waypoints.length - 1]);

    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        code: "Ok",
        routes: [{
          geometry: { type: "LineString", coordinates: geometry },
          distance: 8000,
          duration: 1200
        }]
      })
    });
  };

  await page.route("https://router.project-osrm.org/route/v1/driving/**", handler);
  await page.route("https://routing.openstreetmap.de/routed-car/route/v1/driving/**", handler);
}

async function loadCatalog(page, url) {
  const routesResponsePromise = page.waitForResponse(response =>
    response.url().includes("/api/rutas-con-coordenadas") && response.status() === 200
  );
  const neighborhoodsResponsePromise = page.waitForResponse(response =>
    response.url().includes("/api/barrios/todos") && response.status() === 200
  );
  await page.goto(url);

  const [routesResponse, neighborhoodsResponse] = await Promise.all([
    routesResponsePromise,
    neighborhoodsResponsePromise
  ]);
  return {
    routes: await routesResponse.json(),
    neighborhoods: await neighborhoodsResponse.json()
  };
}

async function simulateCatalogRoutes(page, routes, neighborhoods, roadRequests, context) {
  const candidates = selectableRoutes(routes);
  expect(candidates, `The ${context} validation server returned no selectable active routes`)
    .not.toHaveLength(0);
  const barrioNames = new Set(neighborhoods.map(barrio => normalizeName(barrio.nombre)));
  const missingNeighborhoods = [];
  const failures = [];

  for (const route of candidates) {
    const barrios = route.barriosOrdenados;
    for (const barrio of barrios) {
      if (!barrioNames.has(normalizeName(barrio))) {
        missingNeighborhoods.push(`${route.nombre}: ${barrio}`);
      }
    }

    try {
      await test.step(`${context}: ${route.nombre} (${barrios[0]} → ${barrios[barrios.length - 1]})`, async () => {
        await page.locator("#origen").fill(barrios[0]);
        await expect(page.locator("#destino")).toBeEnabled();
        await page.locator("#destino").fill(barrios[barrios.length - 1]);
        await page.locator("#btnPlanificar").click();

        const routeCard = page.locator(".tramo").filter({ hasText: route.nombre }).first();
        await expect(routeCard, `Route ${route.id} was not offered by the planner`).toBeVisible();
        await routeCard.click();
        await expect(page.locator("#rutaDetalle")).toBeVisible();
        await expect(page.locator("#map .leaflet-overlay-pane path.leaflet-interactive"))
          .not.toHaveCount(0);
      });
    } catch (error) {
      failures.push(`${route.id} ${route.nombre}: ${error.message}`);
      console.error(`[ROUTE-TEST-FAILED] ${route.id} ${route.nombre}: ${error.message}`);
      await page.reload();
      await expect(page.locator("#btnPlanificar")).toBeVisible();
    }
  }

  console.log(`[ROUTE-CATALOG] API entries=${routes.length}; planner-eligible=${candidates.length}`);
  console.log(`[ROUTE-CATALOG] context=${context}; tested=${candidates.map(route => route.nombre).join(" | ")}`);
  console.log(`[ROUTE-CATALOG] context=${context}; synthetic OSRM requests=${roadRequests.length}`);
  expect(missingNeighborhoods, `${context}: route barrios missing from /api/barrios/todos`)
    .toEqual([]);
  expect(failures, `${context}: planner failures by route`).toEqual([]);
  expect(roadRequests.length, `${context}: no road geometry requests were made`)
    .toBeGreaterThan(0);
  expect(roadRequests.every(request => request.waypointCount >= 2))
    .toBe(true);
}

async function registerAndLogin(page) {
  const suffix = `${Date.now()}${Math.random().toString(36).slice(2, 8)}`;
  const username = `routeuser${suffix}`;
  const password = "safe-password-26";

  await page.goto("/registro-mejorado");
  await page.locator("#nombres").fill("Usuario");
  await page.locator("#apellidos").fill("De rutas");
  await page.locator("#username").fill(username);
  await page.locator("#email").fill(`${username}@example.test`);
  await page.locator("#password").fill(password);
  await page.locator("#passwordConfirm").fill(password);
  await page.locator("#terminos").check();
  await page.locator("#mayorEdad").check();
  await page.locator("#registroForm button[type=submit]").click();
  await expect(page).toHaveURL(/\/inicio-de-sesion-mejorado\?registrado=true$/);

  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.locator("#loginForm button[type=submit]").click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

test("the public planner can select every eligible route in its running catalog", async ({ page }) => {
  test.setTimeout(300000);
  const roadRequests = [];
  await stubRoadRouting(page, roadRequests);
  const catalog = await loadCatalog(page, "/viajar_public");
  await simulateCatalogRoutes(page, catalog.routes, catalog.neighborhoods, roadRequests, "public");
});

test("an authenticated user can select every eligible route in the running catalog", async ({ page }) => {
  test.setTimeout(300000);
  const roadRequests = [];
  await stubRoadRouting(page, roadRequests);
  await registerAndLogin(page);
  const catalog = await loadCatalog(page, "/viajar");
  await simulateCatalogRoutes(page, catalog.routes, catalog.neighborhoods, roadRequests, "private");
});
