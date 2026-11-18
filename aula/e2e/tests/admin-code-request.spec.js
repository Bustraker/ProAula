const { test, expect } = require("@playwright/test");

test("legacy ADMIN request page is retired and the approval inbox stays private", async ({ page }) => {
  await page.goto("/solicitar-codigo-admin");
  await expect(page).toHaveURL(/\/inicio-de-sesion-mejorado$/);
  await expect(page.getByRole("heading", { name: "Inicia sesión" })).toBeVisible();

  await page.goto("/admin/solicitudes-codigo");
  await expect(page).toHaveURL(/\/inicio-de-sesion-mejorado/);
});
