const { test, expect } = require("@playwright/test");
const {
  acceptCurrentConsent,
  login,
  registerAccount,
  revokeConsent,
  submitAcceptedRegistration
} = require("./helpers/legal-consent");

test("reaccepting the current documents restores protected account functionality", async ({ page }) => {
  const username = await registerAccount(page);
  await submitAcceptedRegistration(page, username);
  await revokeConsent(page);

  await login(page, username, { consentRequired: true });
  await expect(page).toHaveURL(/\/legal\/consentir$/);
  await expect(page.locator('input[name="legalConsent"]')).not.toBeChecked();
  await expect(page.locator('input[name="adultConfirmed"]')).not.toBeChecked();

  const deniedStatus = await page.evaluate(async () => {
    const response = await fetch("/api/favoritos");
    return response.status;
  });
  expect(deniedStatus).toBe(403);

  await acceptCurrentConsent(page);
  const restoredStatus = await page.evaluate(async () => {
    const response = await fetch("/api/favoritos");
    return response.status;
  });
  expect(restoredStatus).toBe(200);
  await expect(page.locator("#withdrawConsentTitle")).toBeVisible();
});
