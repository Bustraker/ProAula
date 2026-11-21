const { test, expect } = require("@playwright/test");

async function prepareTrip(page, onArrivalRequest) {
  await page.route("https://unpkg.com/leaflet@1.9.4/dist/leaflet.js", async route => {
    const response = await route.fetch();
    await route.fulfill({ response });
  });
  await page.addInitScript(() => {
    const listeners = new Map();
    let nextListener = 1;
    const current = {
      coords: { latitude: 10.39, longitude: -75.56, accuracy: 5 },
      timestamp: Date.now()
    };
    Object.defineProperty(navigator, "geolocation", {
      configurable: true,
      value: {
        watchPosition(success) {
          const id = nextListener++;
          listeners.set(id, success);
          success(current);
          return id;
        },
        clearWatch(id) {
          listeners.delete(id);
        }
      }
    });
  });

  await page.route("**/api/barrios/todos", route => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify([
      { nombre: "Barrio Origen", latitud: 10.39, longitud: -75.56 },
      { nombre: "Centro", latitud: 10.4, longitud: -75.55 },
      { nombre: "Barrio Destino", latitud: 10.41, longitud: -75.54 }
    ])
  }));
  await page.route("**/api/rutas-con-coordenadas", route => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify([{
      id: "e2e-live-route",
      nombre: "Ruta en vivo",
      barrios: ["Barrio Origen", "Centro", "Barrio Destino"],
      barriosOrdenados: ["Barrio Origen", "Centro", "Barrio Destino"],
      ordenBarriosConfirmado: true,
      coordenadasBarrios: {
        "Barrio Origen": [10.39, -75.56],
        Centro: [10.4, -75.55],
        "Barrio Destino": [10.41, -75.54]
      },
      paradas: [],
      buses: []
    }])
  }));
  await page.route("https://router.project-osrm.org/**", route => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({
      routes: [{
        geometry: {
          type: "LineString",
          coordinates: [[-75.56, 10.39], [-75.55, 10.4], [-75.54, 10.41]]
        },
        distance: 3000,
        duration: 900
      }]
    })
  }));
  await page.route("**/api/llegadas/**", onArrivalRequest);

  await page.goto("/viajar_public");
  await page.locator("#btnSeguirUbicacion").click();
  await page.locator("#origen").fill("Barrio Origen");
  await page.locator("#destino").fill("Barrio Destino");
  await page.locator("#btnPlanificar").click();
  await expect(page.locator("#rutaDetalle")).toBeVisible();
  await page.locator("#detalleResumen").click();
  await expect(page.locator("#rutaDetalle")).toHaveAttribute("open", "");
  await expect(page.locator("#llegadasRutaPanel")).toBeVisible();
}

test("shows an approximate bus marker and alerts once when it is near the user", async ({ page }) => {
  let consultations = 0;
  await prepareTrip(page, route => {
    consultations++;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        estado: "SIN_VELOCIDAD",
        mensaje: "Hay ubicación compartida, pero aún no hay suficientes reportes en movimiento para estimar llegadas.",
        actualizadoEn: new Date().toISOString(),
        reportantesActivos: 1,
        llegadas: [],
        ubicacionEstimada: {
          latitud: 10.391,
          longitud: -75.559,
          actualizadoEn: new Date().toISOString(),
          confianza: "BAJA"
        }
      })
    });
  });

  const status = page.locator(".arrival-card__status");
  await expect(status).toContainText("ubicación aproximada marcada en el mapa");
  await expect(page.locator(".arrival-bus-marker")).toHaveCount(1);
  await expect(page.locator("#mu-avisos")).toContainText("bus estimado");
  await expect(status).not.toHaveAttribute("aria-busy", "true");
  await test.info().attach("approximate-bus-arrival-screenshot", {
    body: await page.screenshot(),
    contentType: "image/png"
  });

  await page.locator(".arrival-card__direction").dispatchEvent("change");
  await expect.poll(() => consultations).toBe(2);
  await expect(page.locator(".arrival-bus-marker")).toHaveCount(1);
  await expect(page.locator("#mu-avisos .mu-aviso").filter({ hasText: "bus estimado" })).toHaveCount(1);
});

test("keeps the selected route visible without reports and explains API errors", async ({ page }) => {
  let consultations = 0;
  let serviceUnavailable = false;
  await prepareTrip(page, route => {
    consultations++;
    if (serviceUnavailable) {
      return route.fulfill({ status: 503, contentType: "application/json", body: "{}" });
    }
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        estado: "SIN_REPORTES",
        mensaje: "No hay reportes recientes de pasajeros en esta ruta; el recorrido seleccionado sigue visible.",
        actualizadoEn: null,
        reportantesActivos: 0,
        llegadas: [],
        ubicacionEstimada: null
      })
    });
  });

  const status = page.locator(".arrival-card__status");
  await expect(status).toContainText("recorrido seleccionado sigue visible");
  await expect(page.locator(".arrival-bus-marker")).toHaveCount(0);
  await expect(page.locator("#map .leaflet-overlay-pane path").first()).toBeVisible();
  await expect(status).not.toHaveAttribute("aria-busy", "true");

  serviceUnavailable = true;
  await page.locator(".arrival-card__direction").dispatchEvent("change");
  await expect.poll(() => consultations).toBe(2);
  await expect(status).toContainText("No se pudieron consultar las llegadas ni la ubicación estimada");
  await expect(page.locator("#map .leaflet-overlay-pane path").first()).toBeVisible();
});
