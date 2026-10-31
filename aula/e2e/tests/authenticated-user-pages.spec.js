const { test, expect } = require("@playwright/test");

async function registerAndLogin(page) {
  const suffix = Date.now().toString();
  const username = `member${suffix}`;
  const password = "safe-password-26";

  await page.goto("/registro-mejorado");
  await page.locator("#nombres").fill("Usuario");
  await page.locator("#apellidos").fill("Autenticado");
  await page.locator("#username").fill(username);
  await page.locator("#email").fill(`${username}@example.test`);
  await page.locator("#password").fill(password);
  await page.locator("#passwordConfirm").fill(password);
  await page.locator("#terminos").check();
  await page.locator("#registroForm button[type=submit]").click();
  await expect(page).toHaveURL(/\/inicio-de-sesion-mejorado\?registrado=true$/);

  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.locator("#loginForm button[type=submit]").click();
  await expect(page).toHaveURL(/\/dashboard$/);

  return { username, password };
}

test("authenticated dashboard shortcuts and support modal work", async ({ page }) => {
  await registerAndLogin(page);
  await expect(page.getByRole("heading", { name: /Hola, Usuario/i })).toBeVisible();

  await page.locator("#openContactModalHero").click();
  await expect(page.getByRole("dialog", { name: /Contacto rápido/i })).toBeVisible();
  await page.locator("#modalNombre").fill("Usuario");
  await page.locator("#modalApellido").fill("Autenticado");
  await page.locator("#modalTelefono").fill("3005550101");
  await page.locator("#modalEmail").fill("usuario@example.test");
  await page.locator("#modalMensaje").fill("Consulta desde el panel de usuario");
  await page.locator("#contactModalForm button[type=submit]").click();
  await expect(page.locator("#contactSuccessAlert")).toContainText(/Mensaje enviado con éxito/i);
  await expect(page.getByRole("dialog", { name: /Contacto rápido/i })).toBeHidden({ timeout: 5000 });

  await page.locator('a[data-target="buses-section"]').click();
  await expect(page).toHaveURL(/#buses-section$/);
  await expect(page.locator("#buses-section")).toBeInViewport();
  await page.getByRole("link", { name: /Mi Perfil/i }).click();
  await expect(page).toHaveURL(/\/perfil$/);
});

test("authenticated profile updates identity and validates password changes", async ({ page }) => {
  const { username, password } = await registerAndLogin(page);
  await page.goto("/perfil");
  await expect(page.getByRole("heading", { name: "Tu perfil" })).toBeVisible();
  await expect(page.locator(".profile-tag").filter({ hasText: username })).toBeVisible();

  await page.locator("#nombres").fill("Nombre actualizado");
  await page.locator("#email").fill(`${username}.updated@example.test`);
  await page.locator('form[action="/perfil/actualizar"] button[type=submit]').click();
  await expect(page.getByRole("status")).toContainText(/Perfil actualizado exitosamente/i);
  await expect(page.getByRole("heading", { name: "Nombre actualizado Autenticado" })).toBeVisible();

  await page.locator("#passwordActual").fill(password);
  await page.locator("#passwordNuevo").fill("new-safe-password-27");
  await page.locator("#passwordConfirmar").fill("does-not-match");
  await expect(page.locator("#passwordMatch")).toContainText(/no coinciden/i);
  await expect(page.locator("#passwordConfirmar")).toHaveJSProperty("validationMessage", "Las contraseñas no coinciden.");

  await page.locator("#passwordConfirmar").fill("new-safe-password-27");
  await page.locator("#passwordNuevo").fill("new-safe-password-27");
  await page.locator('[data-password-toggle][aria-controls="passwordNuevo"]').click();
  await expect(page.locator("#passwordNuevo")).toHaveAttribute("type", "text");
  await page.locator('[data-password-toggle][aria-controls="passwordNuevo"]').click();
  await expect(page.locator("#passwordNuevo")).toHaveAttribute("type", "password");
  await page.locator('form[action="/perfil/cambiar-password"] button[type=submit]').click();
  await expect(page.getByRole("status")).toContainText(/Contraseña cambiada exitosamente/i);

  await page.locator('form[action="/logout"] button[type="submit"]').click();
  await expect(page).toHaveURL(/\/$/);
  await page.goto("/inicio-de-sesion-mejorado");
  await page.locator("#username").fill(username);
  await page.locator("#password").fill("new-safe-password-27");
  await page.locator("#loginForm button[type=submit]").click();
  await expect(page).toHaveURL(/\/dashboard$/);
});

test("authenticated history, route directory, trip planner and contact page are usable", async ({ page }) => {
  await registerAndLogin(page);

  await page.goto("/historial");
  await expect(page.getByRole("heading", { name: "Bitácora de viajes" })).toBeVisible();
  await expect(page.getByRole("status")).toContainText(/Tu primera historia empieza aquí/i);
  await page.getByRole("link", { name: /Explorar opciones para viajar/i }).click();
  await expect(page).toHaveURL(/\/viajar$/);
  await expect(page.getByRole("heading", { name: /Consulta de rutas/i })).toBeVisible();
  await expect(page.locator("#btnSeguirUbicacion")).toBeEnabled({ timeout: 15000 });

  await page.goto("/historial/999999");
  await expect(page.locator(".history-detail--warning")).toContainText(/no tienes permiso para verlo/i);

  await page.goto("/rutas");
  await expect(page.getByRole("heading", { name: /Encuentra tu próximo recorrido/i })).toBeVisible();
  await page.locator('input[name="buscar"]').fill("ruta-inexistente-e2e");
  await page.getByRole("button", { name: /Aplicar filtros/i }).click();
  await expect(page).toHaveURL(/buscar=ruta-inexistente-e2e/);
  await expect(page.getByRole("heading", { name: /No encontramos ese recorrido/i })).toBeVisible();
  await page.getByRole("link", { name: /Ver todas las rutas/i }).click();
  await expect(page).toHaveURL(/\/rutas$/);
  await expect(page.locator(".js-toggle-vista")).toBeEnabled();

  await page.goto("/contacto_usuario");
  await expect(page.getByRole("heading", { name: /Tu experiencia también nos guía/i })).toBeVisible();
  await page.locator("#contacto-nombre").fill("Usuario");
  await page.locator("#contacto-apellido").fill("Autenticado");
  await page.locator("#contacto-email").fill("usuario@example.test");
  await page.locator("#contacto-telefono").fill("3005550101");
  await page.locator("#contacto-mensaje").fill("Consulta desde la página de contacto autenticada");
  await page.locator(".member-contact-form button[type=submit]").click();
  await expect(page.getByRole("status")).toContainText(/Gracias por tu mensaje/i);
});

test("authenticated incident report guides entry and handles route availability", async ({ page }) => {
  await registerAndLogin(page);
  await page.goto("/incidentes/nuevo");

  await expect(page.getByRole("heading", { name: "Reportar un incidente" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Un buen reporte ayuda" })).toBeVisible();
  await expect(page.locator(".incident-card")).toHaveCSS("animation-name", "incident-enter");
  await page.emulateMedia({ reducedMotion: "reduce" });
  const animationDuration = await page.locator(".incident-card")
    .evaluate(element => Number.parseFloat(getComputedStyle(element).animationDuration));
  expect(animationDuration).toBeLessThan(0.001);
  await page.setViewportSize({ width: 390, height: 844 });
  const layoutColumns = await page.locator(".incident-layout")
    .evaluate(element => getComputedStyle(element).gridTemplateColumns.split(" ").length);
  expect(layoutColumns).toBe(1);
  await expect(page.locator("#descripcion-contador")).toHaveText("0 / 2000");
  await page.locator("#descripcion").fill("Retraso reportado desde la aplicación.");
  await expect(page.locator("#descripcion-contador")).toHaveText("38 / 2000");

  const routeValues = await page.locator("#rutaId option").evaluateAll(options =>
    options.map(option => option.value).filter(Boolean));
  if (routeValues.length === 0) {
    await expect(page.locator("#ruta-ayuda")).toBeVisible();
    await expect(page.getByRole("button", { name: "Enviar reporte" })).toBeDisabled();
    return;
  }

  await page.locator("#tipo").selectOption("RETRASO");
  await page.locator("#rutaId").selectOption(routeValues[0]);
  await page.getByRole("button", { name: "Enviar reporte" }).click();
  await expect(page).toHaveURL(/\/incidentes\/nuevo$/);
  await expect(page.getByRole("status")).toContainText(/Incidente #\d+ registrado correctamente/i);
});
