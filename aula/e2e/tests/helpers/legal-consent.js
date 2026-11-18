const { expect } = require("@playwright/test");
const { randomUUID } = require("node:crypto");

const TEST_PASSWORD = "safe-password-26";

async function registerAccount(page) {
  const suffix = randomUUID().replaceAll("-", "").slice(0, 10);
  const username = `legal${suffix}`;

  await page.goto("/registro-mejorado");
  await page.locator("#nombres").fill("Prueba");
  await page.locator("#apellidos").fill("Legal");
  await page.locator("#username").fill(username);
  await page.locator("#email").fill(`${username}@example.test`);
  await page.locator("#password").fill(TEST_PASSWORD);
  await page.locator("#passwordConfirm").fill(TEST_PASSWORD);

  return username;
}

async function submitAcceptedRegistration(page, username) {
  await page.locator("#terminos").check();
  await page.locator("#mayorEdad").check();
  await page.locator("#registroForm button[type=submit]").click();
  await expect(page).toHaveURL(/\/inicio-de-sesion-mejorado\?registrado=true$/);
  await login(page, username);
}

async function login(page, username, { consentRequired = false } = {}) {
  await page.goto("/inicio-de-sesion-mejorado");
  await page.locator("#loginForm #username").fill(username);
  await page.locator("#loginForm #password").fill(TEST_PASSWORD);
  await page.locator("#loginForm button[type=submit]").click();
  await expect(page).toHaveURL(consentRequired ? /\/legal\/consentir$/ : /\/dashboard$/);
}

async function revokeConsent(page) {
  await page.goto("/perfil");
  const button = page.getByRole("button", { name: "Retirar autorización" });
  await expect(button).toBeVisible();

  const dialogPromise = page.waitForEvent("dialog");
  const clickPromise = button.click();
  const dialog = await dialogPromise;
  expect(dialog.type()).toBe("confirm");
  await dialog.accept();
  await clickPromise;
  await expect(page).toHaveURL(/\/inicio-de-sesion-mejorado\?consentRevoked=true$/);
}

async function acceptCurrentConsent(page) {
  await expect(page).toHaveURL(/\/legal\/consentir$/);
  await page.locator('input[name="legalConsent"]').check();
  await page.locator('input[name="adultConfirmed"]').check();
  await page.locator('form[action="/legal/consentir"] button[type=submit]').click();
  await expect(page).toHaveURL(/\/perfil$/);
}

module.exports = {
  TEST_PASSWORD,
  acceptCurrentConsent,
  login,
  registerAccount,
  revokeConsent,
  submitAcceptedRegistration
};
