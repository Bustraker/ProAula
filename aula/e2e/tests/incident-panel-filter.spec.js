const fs = require("node:fs");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const incidentScript = path.resolve(__dirname, "../../src/main/resources/static/incidentes.js");
const incidentStyles = path.resolve(__dirname, "../../src/main/resources/static/css/incidentes-panel.css");

const incident = {
  id: 17,
  ruta: "Ruta Centro - Manga",
  tipo: "RETRASO",
  descripcion: "Demora en el recorrido",
  fecha: "2026-10-30T12:00:00",
  estado: "PENDIENTE"
};

function incidentPageMarkup() {
  return `
    <meta name="csrf-token" content="test-token">
    <meta name="csrf-header" content="X-CSRF-TOKEN">
    <main class="panel-shell">
      <section class="panel-card filter-card">
        <form id="filtroIncidentes" class="filter-form">
          <div class="filter-field">
            <label for="filtroTipo">Tipo</label>
            <select id="filtroTipo">
              <option value="">Todos los tipos</option>
              <option value="RETRASO">Retraso</option>
            </select>
          </div>
          <div class="filter-field">
            <label for="filtroEstado">Estado</label>
            <select id="filtroEstado">
              <option value="">Todos los estados</option>
              <option value="PENDIENTE">Pendiente</option>
              <option value="RESUELTO">Resuelto</option>
            </select>
          </div>
          <div class="filter-field filter-field--route">
            <label for="filtroRuta">Ruta</label>
            <input id="filtroRuta">
          </div>
          <div class="filter-actions">
            <button type="submit">Buscar reportes</button>
            <button id="limpiarFiltros" type="button">Limpiar</button>
          </div>
        </form>
      </section>
      <section class="panel-card reports-card">
        <p id="panelFeedback" role="status"></p>
        <p id="totalIncidentes"></p>
        <p id="resumenPagina"></p>
        <span id="paginaActual"></span>
        <button id="paginaAnterior" type="button">Anterior</button>
        <button id="paginaSiguiente" type="button">Siguiente</button>
        <table><tbody id="tablaIncidentes"></tbody></table>
      </section>
    </main>`;
}

test("status and route filters reach the API and updated reports leave the active status result", async ({ page }) => {
  const getRequests = [];
  let stateUpdated = false;

  await page.route("**/api/incidentes**", async route => {
    const request = route.request();
    const url = new URL(request.url());

    if (request.method() === "PATCH") {
      stateUpdated = true;
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ ...incident, estado: "RESUELTO" })
      });
      return;
    }

    getRequests.push(url.searchParams);
    const filteredByPendingStatus = url.searchParams.get("estado") === "PENDIENTE";
    const content = filteredByPendingStatus
      ? stateUpdated ? [] : [incident]
      : [{ ...incident, estado: "RESUELTO" }];
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        content,
        page: Number(url.searchParams.get("page") || 0),
        size: 20,
        totalElements: content.length,
        totalPages: content.length ? 1 : 0,
        first: true,
        last: true
      })
    });
  });

  await page.goto("/");
  await page.setContent(incidentPageMarkup());
  await page.addStyleTag({ content: fs.readFileSync(incidentStyles, "utf8") });
  await page.locator("#filtroEstado").selectOption("PENDIENTE");
  await page.locator("#filtroRuta").fill("Centro");
  await page.addScriptTag({ path: incidentScript });

  await expect(page.locator(".report-id")).toHaveText("REPORTE #17");
  expect(getRequests[0].get("estado")).toBe("PENDIENTE");
  expect(getRequests[0].get("ruta")).toBe("Centro");

  await page.locator(".status-select").selectOption("RESUELTO");
  await page.getByRole("button", { name: "Guardar" }).click();

  await expect(page.locator("#tablaIncidentes")).toContainText("No se encontraron reportes.");
  await expect(page.locator("#totalIncidentes")).toHaveText("0 reportes");
  await expect(page.locator("#panelFeedback")).toContainText(
    "La lista se ajustó al filtro de estado."
  );
  expect(getRequests.at(-1).get("estado")).toBe("PENDIENTE");
  expect(getRequests.at(-1).get("ruta")).toBe("Centro");

  await page.getByRole("button", { name: "Limpiar" }).click();
  await expect(page.locator(".report-id")).toHaveText("REPORTE #17");
  expect(getRequests.at(-1).has("estado")).toBe(false);
  expect(getRequests.at(-1).has("ruta")).toBe(false);
});

test("updating the last filtered row returns pagination to the last available page", async ({ page }) => {
  const getRequests = [];
  let stateUpdated = false;
  const firstPageIncidents = Array.from({ length: 20 }, (_, index) => ({
    ...incident,
    id: index + 1
  }));
  const lastIncident = { ...incident, id: 21 };

  await page.route("**/api/incidentes**", async route => {
    const request = route.request();
    const url = new URL(request.url());

    if (request.method() === "PATCH") {
      stateUpdated = true;
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ ...lastIncident, estado: "RESUELTO" })
      });
      return;
    }

    getRequests.push(url.searchParams);
    const requestedPage = Number(url.searchParams.get("page") || 0);
    let content = [];
    let totalElements = 0;
    let totalPages = 0;

    if (!stateUpdated) {
      content = requestedPage === 0 ? firstPageIncidents : [lastIncident];
      totalElements = 21;
      totalPages = 2;
    } else if (requestedPage === 1) {
      totalElements = 20;
      totalPages = 1;
    } else {
      content = firstPageIncidents;
      totalElements = 20;
      totalPages = 1;
    }

    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        content,
        page: requestedPage,
        size: 20,
        totalElements,
        totalPages,
        first: requestedPage === 0,
        last: requestedPage + 1 >= totalPages
      })
    });
  });

  await page.goto("/");
  await page.setContent(incidentPageMarkup());
  await page.locator("#filtroEstado").selectOption("PENDIENTE");
  await page.addScriptTag({ path: incidentScript });
  await page.getByRole("button", { name: "Siguiente" }).click();
  await expect(page.locator(".report-id")).toHaveText("REPORTE #21");

  await page.locator(".status-select").selectOption("RESUELTO");
  await page.getByRole("button", { name: "Guardar" }).click();

  await expect(page.locator(".report-id").first()).toHaveText("REPORTE #1");
  await expect(page.locator("#paginaActual")).toHaveText("Página 1 de 1");
  await expect(page.locator("#totalIncidentes")).toHaveText("20 reportes");
  await expect(page.locator("#paginaSiguiente")).toBeDisabled();
  expect(getRequests.at(-1).get("page")).toBe("0");
  expect(getRequests.at(-1).get("estado")).toBe("PENDIENTE");
});

test("incident filters fit tablet and mobile panel widths", async ({ page }) => {
  await page.goto("/");
  await page.setContent(incidentPageMarkup());
  await page.addStyleTag({ content: fs.readFileSync(incidentStyles, "utf8") });

  await page.setViewportSize({ width: 800, height: 900 });
  await expect.poll(() => page.locator(".filter-form").evaluate(form =>
    getComputedStyle(form).gridTemplateColumns.split(" ").length)).toBe(2);
  const tabletOverflow = await page.locator(".filter-form")
    .evaluate(form => form.scrollWidth > form.clientWidth);
  expect(tabletOverflow).toBe(false);

  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.locator(".filter-form").evaluate(form =>
    getComputedStyle(form).gridTemplateColumns.split(" ").length)).toBe(1);
  const mobileOverflow = await page.locator(".filter-form")
    .evaluate(form => form.scrollWidth > form.clientWidth);
  expect(mobileOverflow).toBe(false);
});
