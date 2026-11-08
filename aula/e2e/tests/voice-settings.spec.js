const { test, expect } = require("@playwright/test");

async function stubSpeech(page) {
  await page.addInitScript(() => {
    const voices = [
      { name: "Voz de España", lang: "es-ES", voiceURI: "test-es-es" },
      { name: "Voz latinoamericana", lang: "es-419", voiceURI: "test-es-419" },
      { name: "Voz de Colombia", lang: "es-CO", voiceURI: "test-es-co" }
    ];
    window.__spokenUtterances = [];
    window.__speechCancelCount = 0;
    window.SpeechSynthesisUtterance = function (text) {
      this.text = text;
    };
    Object.defineProperty(window, "speechSynthesis", {
      configurable: true,
      value: {
        getVoices: () => voices,
        speak: utterance => window.__spokenUtterances.push({
          text: utterance.text,
          voiceURI: utterance.voice && utterance.voice.voiceURI,
          rate: utterance.rate,
          pitch: utterance.pitch,
          volume: utterance.volume
        }),
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
  await page.locator(".voice-settings > summary").click();

  await expect(page.locator("#voiceSelect option")).toHaveText([
    "Automática (Colombia primero; luego español latinoamericano)",
    "Voz de Colombia (es-CO)",
    "Voz latinoamericana (es-419)",
    "Voz de España (es-ES)"
  ]);
  await expect(page.locator("#voiceStatus")).toContainText("no ofrece un acento cartagenero configurable");

  await page.locator("#btnProbarVoz").click();
  await expect.poll(() => page.evaluate(() => window.__spokenUtterances.length)).toBe(1);
  expect(await page.evaluate(() => window.__spokenUtterances[0].voiceURI)).toBe("test-es-co");

  await page.locator("#voiceSelect").selectOption("test-es-co");
  await page.locator("#voiceRate").fill("1.1");
  await page.locator("#voicePitch").fill("1.1");
  await page.locator("#voiceVolume").fill("0.8");
  await page.locator("#btnProbarVoz").click();

  await expect.poll(() => page.evaluate(() => window.__spokenUtterances.length)).toBe(2);
  expect(await page.evaluate(() => window.__spokenUtterances[1])).toMatchObject({
    voiceURI: "test-es-co",
    rate: 1.1,
    pitch: 1.1,
    volume: 0.8
  });
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem("bustraker.preferenciasVoz"))))
    .toMatchObject({ voiceURI: "test-es-co", rate: 1.1, pitch: 1.1, volume: 0.8 });

  await page.locator("#btnAudioAccesible").click();
  expect(await page.evaluate(() => window.__speechCancelCount)).toBeGreaterThan(0);
  await page.reload();
  await page.locator(".voice-settings > summary").click();
  await expect(page.locator("#voiceSelect")).toHaveValue("test-es-co");
  await expect(page.locator("#voiceRate")).toHaveValue("1.1");
  await expect(page.locator("#btnAudioAccesible")).toHaveAttribute("aria-pressed", "false");
});
