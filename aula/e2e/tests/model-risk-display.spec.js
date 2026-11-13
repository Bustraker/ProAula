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
  let weatherRequest;
  let predictionOrigin = "SINTETICOS_DEMOSTRACION";

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
  await page.route("https://api.open-meteo.com/**", route => {
    weatherRequest = {
      url: route.request().url(),
      headers: route.request().headers()
    };
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        current: {
          time: "2026-11-13T23:00",
          temperature_2m: 29,
          precipitation: 0.3,
          weather_code: 95,
          wind_speed_10m: 6
        },
        hourly: {
          time: ["2026-11-13T23:00", "2026-11-14T00:00", "2026-11-14T01:00", "2026-11-14T02:00"],
          precipitation_probability: [69, 92, 100, 98]
        }
      })
    });
  });
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
        umbral: 0.26,
        modelo: "HistGradientBoostingClassifier (clasificación binaria)",
        origenDatos: predictionOrigin
      })
    });
  });

  await page.goto("/viajar_public");
  await expect(page.locator("#weatherStatus")).toContainText("Tormenta");
  await expect(page.locator("#weatherStatus")).toContainText("probabilidad máxima de lluvia en 3 h: 100%");
  await expect(page.locator(".weather-note")).toContainText("ni modifica la predicción de retraso");
  expect(weatherRequest.headers.referer).toBeUndefined();
  expect(new URL(weatherRequest.url).searchParams.get("forecast_days")).toBe("2");
  await expect(page.locator("#btnSeguirUbicacion")).toBeEnabled({ timeout: 15000 });
  await page.locator("#origen").fill("Barrio A");
  await expect(page.locator("#destino")).toBeEnabled();
  await page.locator("#destino").fill("Barrio B");
  await page.locator("#btnPlanificar").click();

  const risk = page.locator(".tramo__riesgo");
  await expect(risk).toContainText("Alto");
  await expect(risk).toContainText("42%");
  await expect(risk).toContainText("Demostración con datos sintéticos");
  await expect(risk).toContainText("Umbral de alerta: 26%");
  await expect(risk).toHaveClass(/tramo__riesgo--alto/);
  await expect(risk.getByRole("meter")).toHaveAttribute("aria-valuenow", "42");
  await expect(page.locator(".resultados__riesgo-nota"))
    .toContainText("El tiempo no incluye espera ni tráfico.");
  expect(predictionRequest.method).toBe("POST");
  expect(predictionRequest.headers["content-type"]).toContain("application/json");
  expect(predictionRequest.body.ruta).toBe("101");
  expect(predictionRequest.body.diaSemana).toMatch(
    /^(Lunes|Martes|Miércoles|Jueves|Viernes|Sábado|Domingo)$/
  );
  expect(predictionRequest.body.horaSalida).toBeGreaterThanOrEqual(0);
  expect(predictionRequest.body.horaSalida).toBeLessThanOrEqual(23);
  expect(predictionRequest.body.duracionPlanificadaMin).toBe(20);
  expect(predictionRequest.body.indiceDemandaPronosticada).toBeGreaterThanOrEqual(25);
  expect(predictionRequest.body.indiceDemandaPronosticada).toBeLessThanOrEqual(100);

  predictionOrigin = "HISTORICO_REAL";
  await page.reload();
  await expect(page.locator("#btnSeguirUbicacion")).toBeEnabled({ timeout: 15000 });
  await page.locator("#origen").fill("Barrio A");
  await expect(page.locator("#destino")).toBeEnabled();
  await page.locator("#destino").fill("Barrio B");
  await page.locator("#btnPlanificar").click();
  await expect(page.locator(".tramo__riesgo"))
    .toContainText("Entrenado con historial real");
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

test("weather provider failures are shown without blocking route planning", async ({ page }) => {
  await page.route("https://api.open-meteo.com/**", route => route.fulfill({
    status: 503,
    contentType: "application/json",
    body: JSON.stringify({ error: "temporary outage" })
  }));
  await page.goto("/viajar_public");

  await expect(page.locator("#weatherStatus"))
    .toContainText("Clima en línea no disponible");
  await expect(page.locator("#btnPlanificar")).toBeEnabled();
});
