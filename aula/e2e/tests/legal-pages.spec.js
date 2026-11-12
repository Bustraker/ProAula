const { test, expect } = require("@playwright/test");

test("terms and privacy are separate pages with a clear link to each other", async ({ page }) => {
  await page.goto("/terminos");

  await expect(page).toHaveTitle(/Términos y condiciones/i);
  await expect(page.getByRole("heading", { name: "Términos y condiciones" })).toBeVisible();
  await expect(page.locator("#uso")).toContainText("Uso permitido");
  await expect(page.locator("#datos")).toHaveCount(0);

  const privacyLink = page.locator(".legal-switch");
  await expect(privacyLink).toBeVisible();
  await expect(privacyLink).toHaveAttribute("href", "/privacidad");
  await privacyLink.click();

  await expect(page).toHaveURL(/\/privacidad$/);
  await expect(page).toHaveTitle(/Política de privacidad/i);
  await expect(page.getByRole("heading", { name: "Política de privacidad" })).toBeVisible();
  await expect(page.locator("#datos")).toContainText("Cuenta y acceso");
  await expect(page.locator("#ubicacion")).toContainText("90 segundos");

  const termsLink = page.locator(".legal-switch");
  await expect(termsLink).toBeVisible();
  await expect(termsLink).toHaveAttribute("href", "/terminos");
  await termsLink.click();
  await expect(page).toHaveURL(/\/terminos$/);
  await expect(page.getByRole("heading", { name: "Términos y condiciones" })).toBeVisible();
});

test("the legal page switch remains visible and the documents fit mobile screens", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });

  for (const [path, title] of [
    ["/terminos", /Términos y condiciones/i],
    ["/privacidad", /Política de privacidad/i]
  ]) {
    await page.goto(path);
    await expect(page).toHaveTitle(title);
    await expect(page.locator(".legal-switch")).toBeVisible();
    expect(await page.locator("body").evaluate(element => element.scrollWidth)).toBeLessThanOrEqual(390);
  }
});
