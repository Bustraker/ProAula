const { defineConfig } = require("@playwright/test");

const baseURL = process.env.BASE_URL || "http://127.0.0.1:3466";

module.exports = defineConfig({
  testDir: "./tests",
  fullyParallel: false,
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
      JWT_SECRET: "validation-only-key-with-more-than-32-characters"
    },
    url: `${baseURL}/actuator/health`,
    reuseExistingServer: !process.env.CI,
    timeout: 180000
  }
});
