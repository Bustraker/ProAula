const { test, expect } = require("@playwright/test");
const {
  registerAccount,
  submitAcceptedRegistration
} = require("./helpers/legal-consent");

test("registration requires explicit legal consent and records it for the new account", async ({ page }) => {
  const username = await registerAccount(page);

  await expect(page.locator("#terminos")).not.toBeChecked();
  await expect(page.locator("#mayorEdad")).not.toBeChecked();
  await page.locator("#registroForm button[type=submit]").click();
  await expect(page).toHaveURL(/\/registro-mejorado$/);
  await expect(page.locator("#terminos")).not.toBeChecked();
  await expect(page.locator("#mayorEdad")).not.toBeChecked();

  await submitAcceptedRegistration(page, username);
  await page.goto("/perfil");
  await expect(page.locator("#withdrawConsentTitle")).toBeVisible();

  const consentStatus = await page.evaluate(async () => {
    const response = await fetch("/api/favoritos");
    return response.status;
  });
  expect(consentStatus).toBe(200);
});
