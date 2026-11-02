const { test, expect } = require("@playwright/test");

test("administrative access appears centered only after selecting the Bustraker brand", async ({ page }) => {
  await page.goto("/");

  const brand = page.locator("#openAdminGate");
  const gate = page.locator("#adminAccessGate");
  const accessButton = page.locator("#LoginAdminBtn");
  const adminDialog = page.locator("#modalAdmin");

  await expect(brand).toBeVisible();
  await expect(gate).not.toBeVisible();
  await expect(accessButton).not.toBeVisible();

  await brand.click();
  await expect(gate).toBeVisible();
  await expect(accessButton).toBeVisible();

  const buttonBox = await accessButton.boundingBox();
  const viewport = page.viewportSize();
  expect(Math.abs(buttonBox.x + buttonBox.width / 2 - viewport.width / 2)).toBeLessThan(3);
  expect(Math.abs(buttonBox.y + buttonBox.height / 2 - viewport.height / 2)).toBeLessThan(3);
  await expect.poll(() => gate.evaluate(dialog =>
    getComputedStyle(dialog, "::backdrop").backdropFilter
  )).toContain("blur");

  await accessButton.click();
  await expect(gate).not.toBeVisible();
  await expect(adminDialog).toBeVisible();
  await expect(adminDialog).toContainText("Acceso Admin");
  await expect(page.locator("#continueAdmin")).toBeVisible();

  await page.locator("#closeModalAdmin").click();
  await expect(adminDialog).not.toBeVisible();
  await expect(brand).toBeFocused();

  await brand.click();
  await expect(gate).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(gate).not.toBeVisible();
  await expect(brand).toBeFocused();
});
