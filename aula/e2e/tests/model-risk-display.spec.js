const { test, expect } = require("@playwright/test");

test("public route options show the model delay probability before route selection", async ({ page }) => {
  const neighborhoods = [
    { nombre: "Barrio A", latitud: 10.4, longitud: -75.5 },
    { nombre: "Barrio B", latitud: 10.41, longitud: -75.49 }
  ];
  const routes = [{
    id: 101,
    nombre: "Ruta demostrativa",
    estado: "ACTIVA",
    barriosOrdenados: neighborhoods.map(barrio => barrio.nombre),
    ordenBarriosConfirmado: true,
    paradas: [],
    paradasPorSentido: {},
    buses: [],
    coordenadasBarrios: {
      "Barrio A": [10.4, -75.5],
      "Barrio B": [10.41, -75.49]
    },
    coordenadasEstimadas: []
  }];
  let predictionRequest;

  await page.route("**/api/barrios/todos", route => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify(neighborhoods)
  }));
  await page.route("**/api/rutas-con-coordenadas", route => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify(routes)
  }));
  await page.route("https://router.project-osrm.org/**", route => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({
      routes: [{
        geometry: { type: "LineString", coordinates: [[-75.5, 10.4], [-75.49, 10.41]] },
        distance: 1500,
        duration: 600
      }]
    })
  }));
  await page.route("**/api/modelos/retrasos/riesgo", async route => {
    const csrfHeaderName = await page.locator('meta[name="_csrf_header"]').getAttribute("content");
    const csrfToken = await page.locator('meta[name="_csrf"]').getAttribute("content");
    predictionRequest = {
      method: route.request().method(),
      headers: route.request().headers(),
      body: route.request().postDataJSON()
    };
    expect(predictionRequest.headers[csrfHeaderName.toLowerCase()]).toBe(csrfToken);
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        riesgo: "ALTO",
        probabilidadRetraso: 0.42,
        umbral: 0.11,
        modelo: "HistGradientBoostingClassifier (clasificación binaria)",
        origenDatos: "SINTETICOS_DEMOSTRACION"
      })
    });
  });

  await page.goto("/viajar_public");
  await expect(page.locator("#btnSeguirUbicacion")).toBeEnabled({ timeout: 15000 });
  await page.locator("#origen").fill("Barrio A");
  await expect(page.locator("#destino")).toBeEnabled();
  await page.locator("#destino").fill("Barrio B");
  await page.locator("#btnPlanificar").click();

  const risk = page.locator(".tramo__riesgo");
  await expect(risk).toContainText("42%");
  await expect(page.locator(".resultados__riesgo-nota"))
    .toContainText("No incluye semáforos, tráfico ni retrasos reales");
  expect(predictionRequest.method).toBe("POST");
  expect(predictionRequest.headers["content-type"]).toContain("application/json");
  expect(predictionRequest.body.ruta).toBe("101");
  expect(predictionRequest.body.duracionPlanificadaMin).toBe(20);
  expect(predictionRequest.body.indiceDemandaPronosticada).toBeGreaterThanOrEqual(25);
  expect(predictionRequest.body.indiceDemandaPronosticada).toBeLessThanOrEqual(100);
});

test("anonymous route planner can reach the risk endpoint with its CSRF token", async ({ page }) => {
  await page.goto("/viajar_public");
  const status = await page.evaluate(async () => {
    const token = document.querySelector('meta[name="_csrf"]');
    const header = document.querySelector('meta[name="_csrf_header"]');
    const headers = { "Content-Type": "application/json" };
    headers[header.content] = token.content;
    const response = await fetch("/api/modelos/retrasos/riesgo", {
      method: "POST",
      credentials: "same-origin",
      headers,
      body: JSON.stringify({
        ruta: "e2e-public",
        diaSemana: "Lunes",
        horaSalida: 8,
        duracionPlanificadaMin: 30,
        indiceDemandaPronosticada: 50
      })
    });
    return { status: response.status, body: await response.text() };
  });

  expect([200, 503], status.body).toContain(status.status);
});
