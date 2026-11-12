const { test, expect } = require("@playwright/test");

async function stubSpeech(page) {
  await page.addInitScript(() => {
    const voices = [
      { name: "Voz de España", lang: "es-ES", voiceURI: "test-es-es" },
      { name: "Voz latinoamericana", lang: "es-419", voiceURI: "test-es-419" },
      { name: "Voz de Colombia", lang: "es-CO", voiceURI: "test-es-co" }
    ];
    window.__speechVoices = voices;
    window.__spokenUtterances = [];
    window.__speechCancelCount = 0;
    window.SpeechSynthesisUtterance = function (text) {
      this.text = text;
    };
    Object.defineProperty(window, "speechSynthesis", {
      configurable: true,
      value: {
        getVoices: () => window.__speechVoices,
        speak: utterance => {
          if (utterance.volume === 0) return;
          window.__spokenUtterances.push({
            text: utterance.text,
            lang: utterance.lang,
            voiceURI: utterance.voice && utterance.voice.voiceURI,
            rate: utterance.rate,
            pitch: utterance.pitch,
            volume: utterance.volume
          });
        },
        cancel: () => { window.__speechCancelCount += 1; }
      }
    });
  });
}

async function registerAndLogin(page) {
  const username = `voice${Date.now().toString(36)}`;
  const password = "safe-password-26";
  await page.goto("/registro-mejorado");
  await page.locator("#nombres").fill("Usuario");
  await page.locator("#apellidos").fill("Autenticado");
  await page.locator("#username").fill(username);
  await page.locator("#email").fill(`${username}@example.test`);
  await page.locator("#password").fill(password);
  await page.locator("#passwordConfirm").fill(password);
  await page.locator("#terminos").check();
  await page.locator("#mayorEdad").check();
  await page.locator("#registroForm button[type=submit]").click();
  await expect(page).toHaveURL(/\/inicio-de-sesion-mejorado\?registrado=true$/);
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.locator("#loginForm button[type=submit]").click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

test("public route planner does not expose or trigger synthesized voice", async ({ page }) => {
  await stubSpeech(page);
  await page.goto("/viajar_public");
  await expect(page.locator("#btnAudioAccesible, #voiceSelect, #btnProbarVoz, #srAudioAlert")).toHaveCount(0);
  await expect(page.locator('script[src*="notifications-client.js"]')).toHaveCount(0);
  await expect(page.locator(".mu-popup__btn-speak")).toHaveCount(0);
  expect(await page.evaluate(() => window.__spokenUtterances)).toHaveLength(0);
});

test("authenticated voice preferences prioritize Colombian speech and persist", async ({ page }) => {
  await stubSpeech(page);
  await registerAndLogin(page);
  await page.goto("/viajar");
  await page.evaluate(() => localStorage.setItem("bustraker.audioNotificaciones", "true"));
  await page.reload();
  await expect(page.locator("#btnAudioAccesible")).toHaveAttribute("aria-pressed", "false");
  expect(await page.evaluate(() => localStorage.getItem("bustraker.audioNotificaciones"))).toBeNull();
  await expect(page.locator('script[src*="notifications-client.js"]'))
    .toHaveAttribute("src", /v=browser-voice-2/);
  await page.locator(".voice-settings > summary").click();
  await expect(page.locator("#voiceSelect")).toBeVisible();

  await expect(page.locator("#voiceSelect option")).toHaveText([
    "⭐ Automática — mejor voz masculina disponible (recomendado)",
    "✅ Voz de Colombia [es-CO]",
    "☁️ Voz latinoamericana [es-419]",
    "☁️ Voz de España [es-ES]"
  ]);
  await expect(page.locator("#voiceStatus")).toContainText("Voz activa");

  await page.locator("#btnAudioAccesible").click();
  await expect(page.locator("#btnAudioAccesible")).toHaveAttribute("aria-pressed", "true");
  await page.evaluate(() => { window.__speechVoices = []; });
  const immediateUtteranceStart = await page.evaluate(() => window.__spokenUtterances.length);
  await page.locator("#btnProbarVoz").click();
  await expect.poll(() => page.evaluate(
    start => window.__spokenUtterances.length,
    immediateUtteranceStart
  ), { timeout: 500 }).toBeGreaterThan(immediateUtteranceStart);
  const defaultUtterance = await page.evaluate(
    start => window.__spokenUtterances.slice(start)[0],
    immediateUtteranceStart
  );
  expect(defaultUtterance).toMatchObject({ lang: "es-CO" });
  expect(defaultUtterance.voiceURI).toBeUndefined();
  await page.evaluate(() => {
    window.__speechVoices = [
      { name: "Voz de España", lang: "es-ES", voiceURI: "test-es-es" },
      { name: "Voz latinoamericana", lang: "es-419", voiceURI: "test-es-419" },
      { name: "Voz de Colombia", lang: "es-CO", voiceURI: "test-es-co" }
    ];
    window.speechSynthesis.onvoiceschanged();
  });
  await expect(page.locator("#voiceSelect option")).toHaveCount(4);
  await page.locator("#btnProbarVoz").click();
  await expect.poll(() => page.evaluate(() => window.__spokenUtterances.length))
    .toBeGreaterThan(immediateUtteranceStart + 1);
  const automaticUtterances = await page.evaluate(() => window.__spokenUtterances.slice());
  expect(automaticUtterances.some(utterance => utterance.voiceURI === "test-es-co")).toBe(true);

  await page.locator("#voiceSelect").selectOption("test-es-co");
  await page.locator("#voiceRate").fill("1.1");
  await page.locator("#voicePitch").fill("1.1");
  await page.locator("#voiceVolume").fill("0.8");
  const manualUtteranceStart = await page.evaluate(() => window.__spokenUtterances.length);
  await page.locator("#btnProbarVoz").click();

  await expect.poll(() => page.evaluate(
    start => window.__spokenUtterances.length,
    manualUtteranceStart
  )).toBeGreaterThan(manualUtteranceStart);
  const manualUtterances = await page.evaluate(
    start => window.__spokenUtterances.slice(start),
    manualUtteranceStart
  );
  expect(manualUtterances.every(utterance => utterance.voiceURI === "test-es-co"
    && utterance.rate === 1.1 && utterance.pitch === 1.1 && utterance.volume === 0.8)).toBe(true);
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem("bustraker.preferenciasVoz"))))
    .toMatchObject({ voiceURI: "test-es-co", rate: 1.1, pitch: 1.1, volume: 0.8 });

  await page.locator("#btnAudioAccesible").click();
  expect(await page.evaluate(() => window.__speechCancelCount)).toBeGreaterThan(0);
  await page.reload();
  await page.locator(".voice-settings > summary").click();
  await expect(page.locator("#voiceSelect")).toHaveValue("test-es-co");
  await expect(page.locator("#voiceRate")).toHaveValue("1.1");
  await expect(page.locator("#btnAudioAccesible")).toHaveAttribute("aria-pressed", "false");
  expect(await page.evaluate(() => localStorage.getItem("bustraker.audioNotificaciones"))).toBeNull();

  await page.locator("#btnAudioAccesible").click();
  await expect(page.locator("#btnAudioAccesible")).toHaveAttribute("aria-pressed", "true");
  await expect.poll(() => page.evaluate(() =>
    window.__spokenUtterances.some(utterance => utterance.text.includes("Audio y notificaciones por voz activadas"))
  )).toBe(true);
  const spokenBeforeMapSelection = await page.evaluate(() => window.__spokenUtterances.length);
  const map = page.locator("#map");
  const position = await page.evaluate(() => {
    const mapElement = document.querySelector("#map");
    const bounds = mapElement.getBoundingClientRect();
    const candidates = [
      [0.2, 0.4], [0.8, 0.4], [0.2, 0.7], [0.8, 0.7], [0.5, 0.2], [0.5, 0.85]
    ];
    const point = candidates.map(([x, y]) => ({
      x: bounds.left + bounds.width * x,
      y: bounds.top + bounds.height * y
    })).find(({ x, y }) => mapElement.contains(document.elementFromPoint(x, y)));
    if (!point) throw new Error("No unobstructed map point found");
    return { x: point.x - bounds.left, y: point.y - bounds.top };
  });

  await map.click({ position });
  await expect(page.locator(".mu-popup--click")).toHaveCount(0);
  await map.click({ position });
  await expect(page.locator(".mu-popup--click")).toHaveCount(0);
  expect(await page.evaluate(() => window.__spokenUtterances.length)).toBe(spokenBeforeMapSelection);

  await map.click({ position });
  await expect(page.locator(".mu-popup--click")).toBeVisible();
  await expect.poll(() => page.evaluate(() =>
    window.__spokenUtterances.some(utterance => utterance.text.includes("Punto seleccionado en el mapa"))
  )).toBe(true);
});
