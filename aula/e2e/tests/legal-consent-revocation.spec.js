const { test, expect } = require("@playwright/test");
const {
  login,
  registerAccount,
  revokeConsent,
  submitAcceptedRegistration
} = require("./helpers/legal-consent");

test("revoking authorization closes the session and blocks protected pages", async ({ page }) => {
  const username = await registerAccount(page);
  await submitAcceptedRegistration(page, username);
  await revokeConsent(page);

  await expect(page.getByText(/Retiraste tu autorización de tratamiento de datos/))
    .toBeVisible();

  await page.goto("/dashboard");
  await expect(page).toHaveURL(/\/inicio-de-sesion-mejorado$/);

  await login(page, username, { consentRequired: true });
  await expect(page).toHaveURL(/\/legal\/consentir$/);

  const protectedApiStatus = await page.evaluate(async () => {
    const response = await fetch("/api/favoritos");
    return response.status;
  });
  expect(protectedApiStatus).toBe(403);
});
