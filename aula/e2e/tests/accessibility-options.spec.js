const { test, expect } = require("@playwright/test");

test("public route pages provide persistent visual options and stop accessibility filtering", async ({ page }) => {
  await page.goto("/rutas");
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
