const { test, expect } = require("@playwright/test");

async function expectMapTiles(page) {
  await expect(page.locator("#map")).toBeVisible();
  await expect.poll(async () => page.locator("#map img.leaflet-tile").evaluateAll(
    tiles => tiles.some(tile => tile.complete && tile.naturalWidth > 0)
  ), { timeout: 20000 }).toBe(true);
}

test("public navigation loads route data and supports trip planning inputs", async ({ page }) => {
  await page.context().grantPermissions(["geolocation"]);
  await page.context().setGeolocation({ latitude: 10.4, longitude: -75.5 });

  const barriosResponse = page.waitForResponse(
    response => response.url().includes("/api/barrios/todos") && response.status() === 200
  );
  const rutasResponse = page.waitForResponse(
    response => response.url().includes("/api/rutas-con-coordenadas") && response.status() === 200
  );

  await page.goto("/index_3_public");
  await page.getByRole("link", { name: /Planear mi viaje/i }).click();
  await expect(page).toHaveURL(/\/viajar_public(?:\?.*)?$/);
  await expect(page.getByRole("heading", { name: /Consulta de rutas/i })).toBeVisible();
  await Promise.all([barriosResponse, rutasResponse]);
  await expectMapTiles(page);

  await page.locator(".mu-estilos__boton").click();
  await expect(page.locator('[data-estilo="Claro"]')).toBeVisible();
  const previewAnimations = await page.locator(".mu-estilos__menu").evaluate(menu => ({
    calles: getComputedStyle(menu.querySelector(".mu-mini-calles__ruta")).animationName,
    oscuro: getComputedStyle(menu.querySelector(".mu-mini-oscuro__estrella-a")).animationName,
    claro: getComputedStyle(menu.querySelector(".mu-mini-claro__rayos")).animationName,
    satelite: getComputedStyle(menu.querySelector(".mu-mini-sat__barrido")).animationName,
    callesEsri: getComputedStyle(menu.querySelector(".mu-mini-osm__ruta")).animationName
  }));
  expect(new Set(Object.values(previewAnimations)).size).toBe(5);
  expect(Object.values(previewAnimations)).not.toContain("none");
  const previewLayers = await page.locator(".mu-estilos__menu").evaluate(menu => ({
    calles: menu.querySelectorAll(".mu-mini-calles__resplandor, .mu-mini-calles__nodo, .mu-mini-calles__vehiculo").length,
    oscuro: menu.querySelectorAll(".mu-mini-oscuro__calles, .mu-mini-oscuro__ruta, .mu-mini-oscuro__baliza").length,
    claro: menu.querySelectorAll(".mu-mini-claro__ruta, .mu-mini-claro__pin, .mu-mini-claro__rayos").length,
    satelite: menu.querySelectorAll(".mu-mini-sat__reticula, .mu-mini-sat__anillo, .mu-mini-sat__objetivo").length,
    callesEsri: menu.querySelectorAll(".mu-mini-osm__poi circle, .mu-mini-osm__halo, .mu-mini-osm__marcador, .mu-mini-osm__parada").length
  }));
  expect(Object.values(previewLayers).every(layerCount => layerCount >= 3)).toBe(true);
  const activePreviewAnimations = await page.locator(".mu-estilos__menu").evaluate(menu =>
    Array.from(menu.querySelectorAll(".mu-estilo__muestra svg")).map(svg =>
      Array.from(svg.querySelectorAll("*")).filter(element => getComputedStyle(element).animationName !== "none").length
    )
  );
  expect(activePreviewAnimations).toHaveLength(5);
  expect(activePreviewAnimations.every(animationCount => animationCount >= 4)).toBe(true);
  await page.emulateMedia({ reducedMotion: "reduce" });
  const reducedMotionAnimations = await page.locator(".mu-estilos__menu").evaluate(menu =>
    Array.from(menu.querySelectorAll(".mu-estilo__muestra *"), element => getComputedStyle(element).animationName)
  );
  expect(reducedMotionAnimations.every(animationName => animationName === "none")).toBe(true);
  await page.emulateMedia({ reducedMotion: "no-preference" });
  const clearTilesResponse = page.waitForResponse(
    response => response.url().includes("World_Light_Gray_Base") && response.status() === 200
  );
  await page.locator('[data-estilo="Claro"]').click();
  await clearTilesResponse;
  await expect(page.locator('[data-estilo="Claro"]')).toHaveAttribute("aria-checked", "true");
  await expectMapTiles(page);

  const origenes = page.locator("#listaOrigen option");
  await expect(origenes.first()).toHaveAttribute("value", /.+/);
  const origen = await origenes.first().getAttribute("value");
  await page.locator("#origen").fill(origen);
  await expect(page.locator("#destino")).toBeEnabled();
  const destinos = page.locator("#listaDestino option");
  await expect(destinos.first()).toHaveAttribute("value", /.+/);
  const destino = await destinos.first().getAttribute("value");
  await page.locator("#destino").fill(destino);
  await page.locator("#btnPlanificar").click();
  await expect(page.locator("#resultados")).toContainText("No hay combinación de rutas");

  await page.locator("#btnInvertir").click();
  await expect(page.locator("#origen")).toHaveValue(destino);
  await expect(page.locator("#destino")).toHaveValue(origen);

  await page.locator("#btnPanel").click();
  await expect(page.locator("#btnPanel")).toHaveAttribute("aria-expanded", "false");
  await page.locator("#btnPanel").click();
  await expect(page.locator("#btnPanel")).toHaveAttribute("aria-expanded", "true");

  await expect(page.locator("#btnSeguirUbicacion")).toBeEnabled();
  await expect(page.locator("#btnSeguirUbicacion")).toHaveAttribute("aria-pressed", "true");
  await page.locator("#btnSeguirUbicacion").click();
  await expect(page.locator("#btnSeguirUbicacion")).toHaveAttribute("aria-pressed", "false");
  await page.locator("#btnSeguirUbicacion").click();
  await expect(page.locator("#btnSeguirUbicacion")).toHaveAttribute("aria-pressed", "true");
  await page.locator("#btnSeguirUbicacion").click();

  await page.locator("#btnLimpiar").click();
  await expect(page.locator("#origen")).toHaveValue("");
  await expect(page.locator("#destino")).toBeDisabled();
  await expect(page.locator("#listaDestino option")).toHaveCount(0);
  await page.getByText("Opciones del mapa").click();
  await page.locator("label.interruptor").click();
  await expect(page.locator("#mostrarParadas")).not.toBeChecked();
  await page.locator("label.interruptor").click();
  await expect(page.locator("#mostrarParadas")).toBeChecked();
});

test("a trip draws its route and clearly labels approximate neighborhood coordinates", async ({ page }) => {
  const route = {
    id: "e2e-map-route",
    nombre: "Ruta de prueba del mapa",
    barrios: ["El Rodeo", "Centro"],
    barriosOrdenados: ["El Rodeo", "Centro"],
    ordenBarriosConfirmado: true,
    coordenadasBarrios: {
      "El Rodeo": [10.39, -75.56],
      Centro: [10.4236, -75.5478]
    },
    coordenadasEstimadas: ["El Rodeo"],
    paradas: [],
    buses: []
  };
  await page.route("**/api/rutas-con-coordenadas", routeRequest => routeRequest.fulfill({
    contentType: "application/json",
    body: JSON.stringify([route])
  }));
  await page.route("https://router.project-osrm.org/**", routeRequest => routeRequest.fulfill({
    contentType: "application/json",
    body: JSON.stringify({
      routes: [{
        geometry: {
          type: "LineString",
          coordinates: [[-75.56, 10.39], [-75.55, 10.405], [-75.5478, 10.4236]]
        },
        distance: 4200,
        duration: 900
      }]
    })
  }));

  const neighborhoodsResponse = page.waitForResponse(
    response => response.url().includes("/api/barrios/todos") && response.status() === 200
  );
  const routesResponse = page.waitForResponse(
    response => response.url().includes("/api/rutas-con-coordenadas") && response.status() === 200
  );
  await page.goto("/viajar_public");
  const [neighborhoods] = await Promise.all([neighborhoodsResponse, routesResponse]);
  const neighborhoodData = await neighborhoods.json();
  expect(neighborhoodData.find(item => item.nombre === "El Rodeo").coordenadaEstimada).toBe(true);

  await page.locator("#origen").fill("El Rodeo");
  await page.locator("#destino").fill("Centro");
  await page.locator("#btnPlanificar").click();

  await expect(page.locator("#rutaDetalle")).toBeVisible();
  await expect(page.getByText(/ubicaciones aproximadas de barrio/i)).toBeVisible();
  await expect(page.locator("#map .leaflet-overlay-pane path.leaflet-interactive")).not.toHaveCount(0);
  await expect(page.locator("#map .mu-tooltip").filter({ hasText: "Ubicación aproximada" })).toBeVisible();
  await expect(page.locator("#rutaStats")).toContainText("4.2 km");
});

test("a transfer itinerary lists every registered stop in route order without inventing a walk", async ({ page }) => {
  const barrios = [
    { nombre: "Barrio Origen", latitud: 10.39, longitud: -75.56 },
    { nombre: "Centro", latitud: 10.41, longitud: -75.55 },
    { nombre: "Barrio Destino", latitud: 10.43, longitud: -75.54 }
  ];
  const routes = [
    {
      id: "e2e-transfer-route-a",
      nombre: "Ruta A",
      barrios: ["Barrio Origen", "Centro"],
      barriosOrdenados: ["Barrio Origen", "Centro"],
      ordenBarriosConfirmado: true,
      coordenadasBarrios: {
        "Barrio Origen": [10.39, -75.56],
        Centro: [10.41, -75.55]
      },
      paradas: [
        { nombre: "Terminal del Origen", barrio: "Barrio Origen", orden: 1 },
        { nombre: "Punto sin barrio A", barrio: null, orden: 2 },
        { nombre: "Parada del Centro norte", barrio: "Centro", orden: 3 }
      ],
      buses: []
    },
    {
      id: "e2e-transfer-route-b",
      nombre: "Ruta B",
      barrios: ["Centro", "Barrio Destino"],
      barriosOrdenados: ["Centro", "Barrio Destino"],
      ordenBarriosConfirmado: true,
      coordenadasBarrios: {
        Centro: [10.41, -75.55],
        "Barrio Destino": [10.43, -75.54]
      },
      paradas: [
        { nombre: "Parada del Centro sur", barrio: "Centro", orden: 1 },
        { nombre: "Punto sin barrio B", barrio: null, orden: 2 },
        { nombre: "Terminal del Destino", barrio: "Barrio Destino", orden: 3 }
      ],
      buses: []
    }
  ];
  await page.route("**/api/barrios/todos", routeRequest => routeRequest.fulfill({
    contentType: "application/json",
    body: JSON.stringify(barrios)
  }));
  await page.route("**/api/rutas-con-coordenadas", routeRequest => routeRequest.fulfill({
    contentType: "application/json",
    body: JSON.stringify(routes)
  }));
  await page.route("https://router.project-osrm.org/**", routeRequest => routeRequest.fulfill({
    contentType: "application/json",
    body: JSON.stringify({
      routes: [{
        geometry: {
          type: "LineString",
          coordinates: [[-75.56, 10.39], [-75.55, 10.41], [-75.54, 10.43]]
        },
        distance: 5000,
        duration: 1200
      }]
    })
  }));

  await page.goto("/viajar_public");
  await expect(page.locator("#origen")).toBeEnabled();
  await page.locator("#origen").fill("Barrio Origen");
  await page.locator("#destino").fill("Barrio Destino");
  await page.locator("#btnPlanificar").click();
  await expect(page.locator("#rutaDetalle")).toBeVisible();
  await page.locator("#tabInfo").click();

  const panel = page.locator("#panelInfo");
  await expect(panel).toContainText("Transbordo en Centro");
  await expect(panel).toContainText("Toma Ruta A");
  await expect(panel).toContainText("Toma Ruta B");
  await expect(panel).toContainText("Parada del Centro norte");
  await expect(panel).toContainText("Parada del Centro sur");
  await expect(panel).toContainText("No se puede confirmar la caminata porque faltan coordenadas");
  await expect(panel.locator(".itinerario-paradas")).toHaveCount(2);
  const routeAStops = panel.locator(".itinerario-paradas").nth(0).locator("li");
  await expect(routeAStops).toHaveCount(3);
  await expect(routeAStops.nth(0)).toContainText("Abordaje: Terminal del Origen");
  await expect(routeAStops.nth(1)).toContainText("Punto sin barrio A");
  await expect(routeAStops.nth(2)).toContainText("Bajada: Parada del Centro norte");
  const routeBStops = panel.locator(".itinerario-paradas").nth(1).locator("li");
  await expect(routeBStops).toHaveCount(3);
  await expect(routeBStops.nth(0)).toContainText("Abordaje: Parada del Centro sur");
  await expect(routeBStops.nth(1)).toContainText("Punto sin barrio B");
  await expect(routeBStops.nth(2)).toContainText("Bajada: Terminal del Destino");
});

test("GPS starts automatically, lists only nearby route neighborhoods and detects route-like movement", async ({ page }) => {
  await page.addInitScript(() => {
    const listeners = new Map();
    let nextListener = 1;
    let current = {
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
    window.setBustrakerGps = (latitude, longitude, timestamp) => {
      current = {
        coords: { latitude, longitude, accuracy: 5 },
        timestamp
      };
      listeners.forEach(success => success(current));
    };
  });
  const neighborhoods = [
    { nombre: "Barrio Origen", latitud: 10.39, longitud: -75.56 },
    { nombre: "Centro", latitud: 10.4, longitud: -75.55 },
    { nombre: "Barrio Destino", latitud: 10.41, longitud: -75.54 }
  ];
  await page.route("**/api/barrios/todos", routeRequest => routeRequest.fulfill({
    contentType: "application/json",
    body: JSON.stringify(neighborhoods)
  }));
  await page.route("**/api/rutas-con-coordenadas", routeRequest => routeRequest.fulfill({
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
  await page.route("https://router.project-osrm.org/**", routeRequest => routeRequest.fulfill({
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

  await page.goto("/viajar_public");
  await expect(page.locator("#btnSeguirUbicacion")).toHaveAttribute("aria-pressed", "true");
  await page.locator("#origen").fill("Barrio Origen");
  await page.locator("#destino").fill("Barrio Destino");
  await page.locator("#btnPlanificar").click();
  await expect(page.locator("#rutaDetalle")).toBeVisible();
  await expect(page.locator("#rutaStats")).toContainText("3.0 km");

  const statusButton = page.locator(".estado-viaje__boton");
  const statusPanel = page.locator("#panelEstadoViaje");
  await expect(statusPanel).toBeVisible();
  await expect(statusButton).toHaveAttribute("aria-expanded", "true");
  await expect(statusPanel).toContainText("Barrio Origen");
  await expect(statusPanel).not.toContainText("Barrio Destino");

  const baseTime = Date.now();
  for (let step = 1; step <= 3; step++) {
    await page.evaluate(({ step, baseTime }) => {
      window.setBustrakerGps(
        10.39 + step * 0.00015,
        -75.56 + step * 0.00015,
        baseTime + step * 3000
      );
    }, { step, baseTime });
  }
  await expect(statusPanel).toContainText("Posible desplazamiento coincidente con el recorrido");
  await expect(statusButton).toHaveAttribute("aria-expanded", "true");
  await expect(statusPanel).toContainText("La coincidencia con la ruta es aproximada");
});

test("public home and bus directory controls work", async ({ page }) => {
  await page.goto("/index_3_public");
  await expect(page.getByRole("heading", { name: /Tu ciudad, en movimiento/i })).toBeVisible();
  await page.getByRole("link", { name: /Consultar buses/i }).first().click();
  await expect(page).toHaveURL(/\/consultas$/);
  await expect(page.getByRole("heading", { name: /La ciudad, ruta por ruta/i })).toBeVisible();

  const initialCount = await page.locator("#base > .bus-card").count();
  const firstCard = page.locator(".bus-card-trigger").first();
  if (initialCount > 0) {
    await firstCard.click();
    await expect(firstCard).toHaveAttribute("aria-expanded", "true");
    await expect(firstCard.locator("xpath=..").locator(".bus-card-details")).toHaveAttribute("aria-hidden", "false");
    await firstCard.click();
    await expect(firstCard).toHaveAttribute("aria-expanded", "false");
  }

  await page.locator("#toggleTable").click();
  await expect(page.locator("#toggleTable")).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator("#busTableContainer")).toBeVisible();
  await page.locator("#toggleCards").click();
  await expect(page.locator("#toggleCards")).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator("#base")).toHaveJSProperty("hidden", false);

  await page.locator("#searchInput").fill("sin-resultados-para-esta-busqueda");
  await page.locator("#clearButton").click();
  await expect(page.locator("#searchInput")).toHaveValue("");
  await expect(page.locator("#visibleCount")).toHaveText(String(initialCount));
  await page.locator("#searchInput").fill("sin-resultados-para-esta-busqueda");
  if (initialCount > 0) {
    await expect(page.locator("#noResults")).toBeVisible();
    await page.locator("#emptyClearButton").click();
    await expect(page.locator("#noResults")).toBeHidden();
  } else {
    await page.locator("#clearButton").click();
  }
  await expect(page.locator("#searchInput")).toHaveValue("");
  await expect(page.locator("#visibleCount")).toHaveText(String(initialCount));
});

test("public contact form submits and displays its confirmation", async ({ page }) => {
  await page.goto("/index_3_public");
  await page.locator("#contacto-nombre").fill("Ana");
  await page.locator("#contacto-apellido").fill("Prueba");
  await page.locator("#contacto-email").fill("ana.e2e@example.test");
  await page.locator("#contacto-telefono").fill("3005550101");
  await page.locator("#contacto-mensaje").fill("Consulta enviada desde la prueba E2E");
  await page.locator(".contact-form button[type=submit]").click();

  await expect(page.getByText(/Gracias por tu consulta/i)).toBeVisible();
});

test("account registration, login, protected page and logout work in the browser", async ({ page }) => {
  const registrationId = Date.now().toString(36);
  const username = `browser${registrationId}`;
  const email = `${username}@example.test`;
  await page.goto("/registro-mejorado");
  const termsPagePromise = page.waitForEvent("popup");
  await page.getByRole("link", { name: "Términos y Condiciones" }).click();
  const termsPage = await termsPagePromise;
  await expect(termsPage).toHaveTitle(/Términos y Condiciones/i);
  await termsPage.close();
  const privacyPagePromise = page.waitForEvent("popup");
  await page.getByRole("link", { name: "Política de Privacidad" }).click();
  const privacyPage = await privacyPagePromise;
  await expect(privacyPage).toHaveTitle(/Política de Privacidad/i);
  await privacyPage.close();

  await page.locator("#password").fill("safe-password-26");
  await page.locator('[data-password-toggle][aria-controls="password"]').click();
  await expect(page.locator("#password")).toHaveAttribute("type", "text");
  await page.locator('[data-password-toggle][aria-controls="password"]').click();
  await expect(page.locator("#password")).toHaveAttribute("type", "password");
  await page.locator("#passwordConfirm").fill("safe-password-26");
  await page.locator('[data-password-toggle][aria-controls="passwordConfirm"]').click();
  await expect(page.locator("#passwordConfirm")).toHaveAttribute("type", "text");
  await page.locator("#role").selectOption("ADMIN");
  await expect(page.locator("#adminCodeGroup")).toBeVisible();
  await page.locator("#role").selectOption("USER");
  await expect(page.locator("#adminCodeGroup")).toBeHidden();

  await page.locator("#nombres").fill("Usuario");
  await page.locator("#apellidos").fill("E2E");
  await page.locator("#username").fill(username);
  await page.locator("#email").fill(email);
  await page.locator("#password").fill("safe-password-26");
  await page.locator("#passwordConfirm").fill("safe-password-26");
  await page.locator("#terminos").check();
  await page.locator("#registroForm button[type=submit]").click();

  await expect(page).toHaveURL(/\/inicio-de-sesion-mejorado\?registrado=true$/);
  await page.locator("#username").fill(username);
  await page.locator("#password").fill("safe-password-26");
  await page.locator("#loginForm button[type=submit]").click();

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { name: /Hola, Usuario/i })).toBeVisible();
  const privateNeighborhoods = page.waitForResponse(
    response => response.url().includes("/api/barrios/todos") && response.status() === 200
  );
  const privateRoutes = page.waitForResponse(
    response => response.url().includes("/api/rutas-con-coordenadas") && response.status() === 200
  );
  await page.goto("/viajar");
  await expect(page.getByRole("heading", { name: /Consulta de rutas/i })).toBeVisible();
  await Promise.all([privateNeighborhoods, privateRoutes]);
  await expectMapTiles(page);

  await page.goto("/dashboard");
  await expect(page.getByRole("heading", { name: /Hola, Usuario/i })).toBeVisible();
  await page.locator('form[action="/logout"] button[type="submit"]').click();
  await expect(page).toHaveURL(/\/$/);
  await page.goto("/dashboard");
  await expect(page).toHaveURL(/\/inicio-de-sesion-mejorado/);
});
