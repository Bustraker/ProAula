const { defineConfig } = require("@playwright/test");

const evidenceMode = process.env.BUSTRAKER_E2E_BUSLOCATIONEVIDENCE === "true";
const evidencePort = "3467";
const baseURL = evidenceMode
  ? `http://127.0.0.1:${evidencePort}`
  : process.env.BASE_URL || "http://127.0.0.1:3466";

module.exports = defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: "list",
  use: {
    baseURL,
    browserName: "chromium",
    headless: true,
    trace: "retain-on-failure"
  },
  webServer: {
    command: "node start-test-server.js",
    cwd: ".",
    env: {
      JWT_SECRET: "validation-only-key-with-more-than-32-characters",
      BUSTRAKER_E2E_BUSLOCATIONEVIDENCE:
        process.env.BUSTRAKER_E2E_BUSLOCATIONEVIDENCE || "false",
      SERVER_PORT: evidenceMode ? evidencePort : "3466"
    },
    url: `${baseURL}/solicitar-codigo-admin`,
    reuseExistingServer: !evidenceMode && !process.env.CI,
    timeout: 180000
  }
});
