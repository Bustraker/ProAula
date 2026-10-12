# Browser flow tests

Run from this directory:

```powershell
npm ci
npx playwright install chromium
npm test
```

Playwright starts the Spring Boot application on port `3466` with an isolated
in-memory H2 database and a validation-only schema. No production database or
credentials are used. The browser journeys cover public route planning's
empty-state behavior, public contact submission, and user registration,
login, protected-page access, and logout.

The related MockMvc journeys can be run from the project root:

```powershell
.\mvnw.cmd -Dtest="*FlowIntegrationTest" test
```

OAuth provider redirects and routes that require external map services are not
part of this deterministic local suite.
