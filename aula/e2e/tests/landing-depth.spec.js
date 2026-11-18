const { test, expect } = require("@playwright/test");

test("the Puerta del Reloj scene keeps its proportions and animates the road beneath a stationary bus", async ({ page }) => {
  await page.goto("/");

  const frame = page.locator(".landing-frame");
  const scene = page.locator(".landing-cartagena-art");
  const illustration = scene.locator(".ctg-v2-scene");

  await expect(frame).toBeVisible();
  await expect(scene).toBeVisible();
  await expect(frame).toHaveCSS("width", "940px");
  await expect(frame).toHaveCSS("border-radius", "19px");
  await expect(page.locator(".landing-brand img")).toHaveCSS("width", "50px");
  await expect(scene).toHaveCSS("width", "340px");
  await expect(scene).toHaveCSS("aspect-ratio", "1.42 / 1");
  await expect(scene).toHaveAttribute("aria-label", "Buseta en una calle de Cartagena frente a la Puerta del Reloj, con sus ruedas y el pavimento en movimiento");
  await expect(illustration).toBeVisible();
  await expect(illustration).toHaveCSS("opacity", "0.94");
  await expect(scene.locator(".ctg-legacy-scene")).toBeHidden();
  await expect(illustration.locator(".ctg-v2-sun")).toBeVisible();
  await expect(illustration.locator("#ctg-v2-sky stop").nth(3)).toHaveAttribute("stop-color", "#f0c77f");
  await expect(illustration.locator(".ctg-v2-sun")).toHaveAttribute("cy", "85");
  expect(await illustration.evaluate(svg => {
    const sun = svg.querySelector(".ctg-v2-sun");
    const gateway = svg.querySelector(".ctg-v2-gateway");
    return sun.compareDocumentPosition(gateway) === Node.DOCUMENT_POSITION_FOLLOWING;
  })).toBe(true);
  await expect(illustration.locator(".ctg-v2-clock-face")).toBeVisible();
  await expect(illustration.locator(".ctg-v2-gate-arch")).toBeVisible();
  await expect(illustration.locator(".ctg-v2-gate")).toHaveAttribute("d", /^M0 147/);
  await expect(illustration.locator(".ctg-v2-sidewalk")).toBeVisible();
  await expect(illustration.locator(".ctg-v2-road")).toBeVisible();
  await expect(illustration.locator(".ctg-v2-road")).toHaveAttribute("d", /^M0 158q180-4 360 0/);
  await expect(illustration.locator(".ctg-v2-road-flow--center")).toHaveCSS("animation-name", "ctg-v2-road-roll");
  await expect(illustration.locator(".ctg-v2-road-flow--center")).toHaveAttribute("d", /^M-360 194h1080/);
  await expect(illustration.locator(".ctg-v2-bus")).toBeVisible();
  await expect(illustration.locator(".ctg-v2-bus animateMotion")).toHaveCount(0);
  await expect(illustration.locator(".ctg-v2-bus-shell")).toHaveCSS("fill", "url(\"#ctg-v2-bus\")");
  await expect(illustration.locator(".ctg-v2-wheel-spokes")).toHaveCount(2);
  await expect(illustration.locator(".ctg-v2-wheel-spokes").first()).toHaveCSS("animation-name", "ctg-v2-wheel-spin");
  await expect(illustration.locator(".ctg-route-scene, .ctg-route-sea, .ctg-route-stop")).toHaveCount(0);

  const bus = illustration.locator(".ctg-v2-bus");
  const centerRoad = illustration.locator(".ctg-v2-road-flow--center");
  const wheel = illustration.locator(".ctg-v2-wheel-spokes").first();
  const busBoundsBefore = await bus.boundingBox();
  const wheelTransformBefore = await wheel.evaluate(element => getComputedStyle(element).transform);
  const roadOffsetBefore = await centerRoad.evaluate(element => getComputedStyle(element).strokeDashoffset);
  await page.waitForTimeout(300);
  const busBoundsAfter = await bus.boundingBox();
  const wheelTransformAfter = await wheel.evaluate(element => getComputedStyle(element).transform);
  const roadOffsetAfter = await centerRoad.evaluate(element => getComputedStyle(element).strokeDashoffset);
  expect(Math.abs(busBoundsAfter.x - busBoundsBefore.x)).toBeLessThan(0.5);
  expect(Math.abs(busBoundsAfter.y - busBoundsBefore.y)).toBeLessThan(0.5);
  expect(wheelTransformAfter).not.toBe(wheelTransformBefore);
  expect(roadOffsetAfter).not.toBe(roadOffsetBefore);

  const frameBounds = await frame.boundingBox();
  await page.mouse.move(frameBounds.x + 30, frameBounds.y + 30);
  await expect(frame).not.toHaveCSS("--landing-tilt-x", "0deg");
  await expect(frame).not.toHaveCSS("--landing-tilt-y", "0deg");
  await expect(frame).not.toHaveCSS("--landing-shadow-x", "0px");
  await expect(frame).not.toHaveCSS("--landing-shadow-y", "0px");

  await page.mouse.move(frameBounds.x + frameBounds.width / 2, frameBounds.y + frameBounds.height / 2);
  await expect.poll(() => frame.evaluate(element =>
    parseFloat(getComputedStyle(element).getPropertyValue("--landing-lift"))
  )).toBeLessThan(-7);

  await page.mouse.move(0, 0);
  await expect(frame).toHaveCSS("--landing-tilt-x", "0deg");
  await expect(frame).toHaveCSS("--landing-tilt-y", "0deg");
  await expect(frame).toHaveCSS("--landing-lift", "0px");
  await expect(frame).toHaveCSS("--landing-shadow-x", "0px");
  await expect(frame).toHaveCSS("--landing-shadow-y", "0px");
});

test("the primary action is polished and reduced motion preserves a stationary bus", async ({ page }) => {
  const action = page.locator(".landing-primary-action");
  const illustration = page.locator(".ctg-v2-scene");

  await page.goto("/");
  await action.hover();
  await expect(action).toHaveCSS("transform", "matrix(1, 0, 0, 1, 0, -2)");
  await expect.poll(() => action.evaluate(element =>
    getComputedStyle(element, "::before").animationName
  )).toBe("button-light-sweep");

  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.reload();
  await expect(illustration.locator(".ctg-v2-bus")).toBeVisible();
  await expect(illustration.locator(".ctg-v2-bus animateMotion")).toHaveCount(0);
  await expect(illustration.locator(".ctg-v2-wheel-spokes").first()).toHaveCSS("animation-iteration-count", "1");
  await expect(illustration.locator(".ctg-v2-road-flow--center")).toHaveCSS("animation-iteration-count", "1");
});

test("the landing composition stays within a narrow mobile viewport", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");

  const frame = page.locator(".landing-frame");
  const scene = page.locator(".landing-cartagena-art");

  await expect(frame).toBeVisible();
  await expect(scene.locator(".ctg-v2-scene")).toBeVisible();
  await expect(page.locator(".landing-brand img")).toHaveCSS("width", "42px");
  expect(await page.locator("body").evaluate(element => element.scrollWidth)).toBeLessThanOrEqual(390);
  expect((await frame.boundingBox()).width).toBeLessThanOrEqual(358);
  expect((await scene.boundingBox()).width).toBeLessThanOrEqual(320);
});
