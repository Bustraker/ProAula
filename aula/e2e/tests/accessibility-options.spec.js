const { test, expect } = require("@playwright/test");
const fs = require("node:fs");
const path = require("node:path");

function findHtmlFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const entryPath = path.join(directory, entry.name);
    return entry.isDirectory() ? findHtmlFiles(entryPath) : [entryPath];
  }).filter(file => file.endsWith(".html"));
}

test("all application template images declare alternative text", async () => {
  const templateRoot = path.resolve(__dirname, "../../src/main/resources/templates");
  const missingAlt = [];

  findHtmlFiles(templateRoot).forEach(file => {
    const html = fs.readFileSync(file, "utf8");
    const images = html.match(/<img\b[^>]*>/gi) || [];
    images.forEach(image => {
      if (!/\b(?:alt|th:alt)\s*=/i.test(image)) {
        missingAlt.push(`${path.relative(templateRoot, file)}: ${image}`);
      }
    });
  });

  expect(missingAlt).toEqual([]);
});

test("landing page opens and closes accessibility settings on request", async ({ page }) => {
  await page.goto("/");

  const settingsTrigger = page.locator('[data-accessibility-trigger]');
  const controls = page.locator("#accessibilityControls");
  await expect(settingsTrigger).toContainText("Configuraciones");
  await expect(controls).toBeHidden();

  await settingsTrigger.click();
  await expect(controls).toBeVisible();
  await expect(controls).toHaveJSProperty("open", true);
  await expect(settingsTrigger).toHaveAttribute("aria-expanded", "true");
  await expect(page.locator("#accessibilityTextDecrease")).toBeFocused();
  expect(await controls.evaluate(dialog => getComputedStyle(dialog, "::backdrop").backdropFilter))
    .toContain("blur");
  await page.keyboard.press("Escape");
  await expect(controls).toBeHidden();
  await expect(settingsTrigger).toBeFocused();
});

test("accessibility settings fit a narrow screen without obscuring their actions", async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 740 });
  await page.goto("/");
  await page.locator("[data-accessibility-trigger]").click();

  const panel = page.locator("#accessibilityControls");
  await expect(panel).toBeVisible();
  await expect(page.locator("#accessibilityResetAll")).toBeVisible();
  const bounds = await panel.boundingBox();
  expect(bounds).not.toBeNull();
  expect(bounds.x).toBeGreaterThanOrEqual(0);
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(360);
  expect(bounds.y + bounds.height).toBeLessThanOrEqual(740);
});

test("global preferences are inherited and user overrides stay local", async ({ page }) => {
  await page.goto("/");
  await page.locator("[data-accessibility-trigger]").click();

  const controls = page.locator("#accessibilityControls");
  await expect(controls).toContainText("Visión y lectura");
  await expect(controls).toContainText("Movimiento y navegación");
  await expect(controls).toContainText("Comunicación y escucha");
  await expect(page.locator("#accessibilityControlsScope")).toHaveText("CONFIGURACIÓN GENERAL");

  await page.locator('[data-accessibility-toggle="readableText"]').click();
  await page.locator("#accessibilityTextScale").evaluate(input => {
    input.value = "7";
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });
  await expect(page.locator("html")).toHaveClass(/accessibility-readable-text/);
  await expect(page.locator("html")).toHaveClass(/accessibility-text-200/);
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem("bustraker.accessibility.global"))))
    .toMatchObject({ readableText: true, textScale: 200 });

  await page.goto("/rutas");
  await expect(page.locator("html")).toHaveClass(/accessibility-readable-text/);
  await expect(page.locator("html")).toHaveClass(/accessibility-text-200/);
  await page.locator('[data-accessibility-trigger]').first().click();
  await expect(page.locator("#accessibilityControlsScope")).toHaveText("PANEL DE USUARIO");
  await page.locator('[data-accessibility-toggle="readableText"]').click();
  await page.locator('[data-accessibility-toggle="highContrast"]').click();
  await expect(page.locator("html")).not.toHaveClass(/accessibility-readable-text/);
  await expect(page.locator("html")).toHaveClass(/accessibility-high-contrast/);
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem("bustraker.accessibility.user"))))
    .toMatchObject({ readableText: false, highContrast: true });

  await page.goto("/");
  await expect(page.locator("html")).toHaveClass(/accessibility-readable-text/);
  await expect(page.locator("html")).not.toHaveClass(/accessibility-high-contrast/);
  await expect(page.locator("html")).toHaveClass(/accessibility-text-200/);

  await page.goto("/rutas");
  await page.locator('[data-accessibility-trigger]').first().click();
  await page.locator("#accessibilityResetAll").click();
  await expect(page.locator("html")).toHaveClass(/accessibility-readable-text/);
  await expect(page.locator("html")).not.toHaveClass(/accessibility-high-contrast/);
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem("bustraker.accessibility.user"))))
    .toEqual({});
  await page.goto("/");
  await expect(page.locator("html")).toHaveClass(/accessibility-readable-text/);
  await expect(page.locator("html")).toHaveClass(/accessibility-text-200/);
});

test("admin preferences remain isolated and reset to inherited global settings", async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("bustraker.accessibility.global", JSON.stringify({
      readableText: true,
      textScale: 125
    }));
    document.addEventListener("DOMContentLoaded", () => {
      document.body.dataset.accessibilityScope = "admin";
    }, { once: true });
  });
  await page.goto("/");
  await expect(page.locator("#accessibilityControlsScope")).toHaveText("PANEL DE ADMINISTRACIÓN");
  await expect(page.locator("html")).toHaveClass(/accessibility-readable-text/);
  await expect(page.locator("html")).toHaveClass(/accessibility-text-125/);
  await page.locator("[data-accessibility-trigger]").click();
  await page.locator('[data-accessibility-toggle="readableText"]').click();
  await page.locator("#accessibilityControls .accessibility-group").nth(1).locator("summary").click();
  await page.locator('[data-accessibility-toggle="visualAlerts"]').click();
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem("bustraker.accessibility.admin"))))
    .toMatchObject({ readableText: false, visualAlerts: true });
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem("bustraker.accessibility.user"))))
    .toBeNull();

  await page.locator("#accessibilityResetAll").click();
  await expect(page.locator("html")).toHaveClass(/accessibility-readable-text/);
  await expect(page.locator("html")).not.toHaveClass(/accessibility-visual-alerts/);
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem("bustraker.accessibility.admin"))))
    .toEqual({});
});

test("non-global preferences inherit homepage settings without changing them", async ({ page }) => {
  await page.goto("/");
  await page.locator("[data-accessibility-trigger]").click();
  await page.locator('[data-accessibility-toggle="readableText"]').click();

  await page.addInitScript(() => {
    document.addEventListener("DOMContentLoaded", () => {
      document.body.dataset.accessibilityScope = "public";
    }, { once: true });
  });
  await page.goto("/");
  await expect(page.locator("#accessibilityControlsScope")).toHaveText("PREFERENCIAS LOCALES");
  await expect(page.locator("html")).toHaveClass(/accessibility-readable-text/);
  await page.locator("[data-accessibility-trigger]").first().click();
  await page.locator("#accessibilityControls .accessibility-group").nth(1).locator("summary").click();
  await page.locator('[data-accessibility-toggle="reducedMotion"]').click();
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem("bustraker.accessibility.public"))))
    .toMatchObject({ reducedMotion: true });

  await expect(page.locator("html")).toHaveClass(/accessibility-readable-text/);
  await expect(page.locator("html")).toHaveClass(/accessibility-reduced-motion/);
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem("bustraker.accessibility.global"))))
    .toMatchObject({ readableText: true, reducedMotion: false });
});

test("public route pages provide persistent visual options and stop accessibility filtering", async ({ page }) => {
  await page.goto("/rutas");
  const settingsTrigger = page.locator('[data-accessibility-trigger]').first();
  await expect(settingsTrigger).toBeVisible();
  await expect(page.locator("#accessibilityTextIncrease")).toBeHidden();
  await settingsTrigger.click();
  await expect(page.locator("#accessibilityTextIncrease")).toBeVisible();
  await expect(page.locator("#accessibilityContrast")).toHaveAttribute("aria-pressed", "false");

  await page.locator("#accessibilityTextIncrease").click();
  await expect(page.locator("html")).toHaveClass(/accessibility-text-110/);
  await page.locator("#accessibilityTextIncrease").click();
  await expect(page.locator("html")).toHaveClass(/accessibility-text-125/);
  await page.locator("#accessibilityTextDecrease").click();
  await expect(page.locator("html")).toHaveClass(/accessibility-text-110/);
  await page.locator("#accessibilityTextReset").click();
  await expect(page.locator("html")).not.toHaveClass(/accessibility-text-/);

  await page.locator("#accessibilityContrast").click();
  await expect(page.locator("#accessibilityContrast")).toHaveAttribute("aria-pressed", "true");
  await page.reload();
  await page.locator('[data-accessibility-trigger]').first().click();
  await expect(page.locator("#accessibilityContrast")).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator("html")).toHaveClass(/accessibility-high-contrast/);
  expect(await page.locator("body").evaluate(element => getComputedStyle(element).backgroundColor))
    .toBe("rgb(0, 0, 0)");

  const detailPath = await page.locator(".ruta-card a[href^='/ruta/']").first().getAttribute("href");
  expect(detailPath).toBeTruthy();
  await page.goto(detailPath);
  await expect(page.locator("#stopAccessibilityFilter")).toBeVisible();
  await page.evaluate(() => {
    const table = document.createElement("table");
    const body = document.createElement("tbody");
    ["yes", "no", "unknown"].forEach(accessibility => {
      const row = document.createElement("tr");
      row.dataset.accessibilityStatus = accessibility;
      row.innerHTML = `<td>${accessibility}</td>`;
      body.appendChild(row);
    });
    table.appendChild(body);
    document.querySelector("#stopAccessibilityFilter").closest(".card-body").appendChild(table);
  });
  await page.locator("#stopAccessibilityFilter").selectOption("no");
  await expect(page.locator("#stopAccessibilityFilterStatus")).toContainText("paradas mostradas");
  await expect(page.locator('tr[data-accessibility-status="no"]')).toBeVisible();
  await expect(page.locator('tr[data-accessibility-status="yes"]')).toBeHidden();
  await expect(page.locator('tr[data-accessibility-status="unknown"]')).toBeHidden();
  await expect(page.locator("html")).toHaveClass(/accessibility-high-contrast/);
});
