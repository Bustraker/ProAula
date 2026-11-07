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
  await expect(page.locator(".admin-access-stage")).not.toBeVisible();

  await brand.click();
  await expect(gate).toBeVisible();
  await expect(accessButton).toBeVisible();
  await expect(page.locator(".admin-access-blueprint")).toBeVisible();
  await expect(page.locator(".admin-access-stage__status")).toContainText("SISTEMA PROTEGIDO");
  await expect(accessButton).toContainText("Acceso administrativo");
  await expect(accessButton).toContainText("CENTRO DE OPERACIONES");

  const buttonBox = await accessButton.boundingBox();
  const viewport = page.viewportSize();
  expect(buttonBox.width).toBeGreaterThan(300);
  expect(buttonBox.height).toBeGreaterThanOrEqual(80);
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

test("the animated administrative access module fits on narrow screens", async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 740 });
  await page.goto("/");
  await page.locator("#openAdminGate").click();

  const stage = page.locator(".admin-access-stage");
  const button = page.locator("#LoginAdminBtn");
  await expect(stage).toBeVisible();
  await expect(button).toBeVisible();
  await expect(page.locator(".admin-access-stage__side-note--left")).toBeHidden();

  const stageBox = await stage.boundingBox();
  const buttonBox = await button.boundingBox();
  expect(stageBox.width).toBeLessThanOrEqual(360);
  expect(buttonBox.x).toBeGreaterThanOrEqual(0);
  expect(buttonBox.x + buttonBox.width).toBeLessThanOrEqual(360);
  expect(Math.abs(buttonBox.x + buttonBox.width / 2 - 180)).toBeLessThan(3);
});

test("the access animations honor reduced-motion preferences", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await page.locator("#openAdminGate").click();

  const animationDuration = await page.locator(".admin-access-blueprint__tilt").evaluate(element =>
    getComputedStyle(element).animationDuration
  );
  const durationInMilliseconds = animationDuration.endsWith("ms")
    ? Number.parseFloat(animationDuration)
    : Number.parseFloat(animationDuration) * 1000;

  expect(durationInMilliseconds).toBeLessThanOrEqual(0.01);
});
