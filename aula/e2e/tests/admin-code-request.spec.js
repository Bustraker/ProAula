const { test, expect } = require("@playwright/test");

test("ADMIN invite request form validates email and keeps the approval inbox private", async ({ page }) => {
  await page.goto("/solicitar-codigo-admin");
  await expect(page.getByRole("heading", { name: "Solicitar acceso ADMIN" })).toBeVisible();

  const emailField = page.getByLabel("Correo electrónico");
  await expect(emailField).toHaveAttribute("maxlength", "254");
  await emailField.fill("not-an-email");
  await page.getByRole("button", { name: "Enviar solicitud" }).click();

  await expect(emailField).toHaveJSProperty("validity.valid", false);
  await expect(page).toHaveURL(/\/solicitar-codigo-admin$/);

  await page.goto("/admin/solicitudes-codigo");
  await expect(page).toHaveURL(/\/inicio-de-sesion-mejorado/);
});
