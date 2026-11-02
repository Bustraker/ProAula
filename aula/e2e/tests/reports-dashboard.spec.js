const path = require("node:path");
const { test, expect } = require("@playwright/test");

test("analytics dashboard renders live indicators and filters monthly activity", async ({ page }) => {
  const months = Array.from({ length: 12 }, (_, index) => ({
    mes: `mes-${index + 1}`,
    viajesRegistrados: index === 10 ? 7 : 0,
    incidentesReportados: index === 11 ? 2 : 0
  }));
  const indicators = {
    totalBuses: 2,
    totalRutas: 3,
    rutasActivas: 2,
    rutasVerificadas: 2,
    totalUsuarios: 4,
    totalParadas: 8,
    paradasActivas: 7,
    totalViajesRegistrados: 7,
    totalPasajes: 14,
    totalIncidentes: 2,
    incidentesPorAtender: 2,
    totalFavoritos: 5,
    datosSinteticosConfigurados: true,
    flotaPorEstado: { ACTIVO: 1, MANTENIMIENTO: 1, FUERA_SERVICIO: 0 },
    flotaPorOcupacion: { BAJA: 1, MEDIA: 1, ALTA: 0, SIN_DATO: 0 },
    rutasPorEstado: { Activas: 2, Inactivas: 1, Suspendidas: 0, Verificadas: 2 },
    incidentesPorEstado: { PENDIENTE: 2 },
    incidentesPorTipo: [{ nombre: "RETRASO", cantidad: 2 }],
    rutasMasConsultadas: [{ nombre: "Ruta Centro", cantidad: 7 }],
    actividadMensual: months
  };
  const containers = [
    "chartActivity", "chartFleet", "chartIncidentTypes", "chartRoutes",
    "chartOccupancy", "chartRouteStates"
  ];
  const pageMarkup = `
    <meta name="app-context-path" content="/">
    <article class="reports-metric--fleet"><strong class="reports-metric__value"></strong></article>
    <article class="reports-metric--accounts"><strong class="reports-metric__value"></strong></article>
    <strong id="metricActiveRoutes"></strong><strong id="metricOpenIncidents"></strong>
    <span id="reportsDataState"></span><p id="reportsSyntheticNotice" hidden></p>
    <p id="reportsError" role="alert" hidden></p><p id="reportsUpdatedAt"></p>
    <select id="activityPeriod"><option value="12">12 meses</option><option value="6">6 meses</option><option value="3">3 meses</option></select>
    ${containers.map(id => `<div id="${id}"></div>`).join("")}
  `;

  await page.route("**/api/admin/reportes/indicadores", route => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify(indicators)
  }));
  await page.goto("/viajar_public");
  await page.setContent(pageMarkup);
  await page.addScriptTag({
    path: path.resolve(__dirname, "../../src/main/resources/static/js/reports-dashboard.js")
  });
  await page.evaluate(() => document.dispatchEvent(new Event("DOMContentLoaded")));

  await expect(page.locator("#chartActivity svg")).toBeVisible();
  await expect(page.locator("#chartFleet .reports-donut")).toBeVisible();
  await expect(page.locator("#chartIncidentTypes .reports-bar-row")).toHaveCount(1);
  await expect(page.locator("#chartRoutes .reports-bar-row")).toHaveCount(1);
  await expect(page.locator("#chartOccupancy .reports-bar-row")).toHaveCount(2);
  await expect(page.locator("#chartRouteStates .reports-bar-row")).toHaveCount(3);
  await expect(page.locator("#metricOpenIncidents")).toHaveText("2");
  await expect(page.locator("#reportsSyntheticNotice")).toBeVisible();

  await page.locator("#activityPeriod").selectOption("6");
  await expect(page.locator(".reports-activity-month-label")).toHaveCount(6);
});
