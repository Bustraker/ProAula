# ADMIN access-code approval

The ADMIN signup code is issued only after an administrator approves a request.
The approval screen and its POST actions require an authenticated account with
the `ADMIN` role whose account email matches `ADMIN_APPROVER_EMAIL`.

## Request and approval flow

1. An applicant submits an email at `/solicitar-codigo-admin`.
2. Bustraker emails `ADMIN_APPROVER_EMAIL` and sends an FCM push to devices
   registered from `/admin/solicitudes-codigo`.
3. The approver opens the inbox while signed in and chooses approve or reject.
   Approval sends a random, 24-hour, single-use invitation to the applicant's
   email. Rejection sends no code.
4. The applicant registers with that same email and the invitation. The
   invitation is stored as a SHA-256 digest and consumed in the registration
   transaction.

The admin-login verification codes are also stored as one-way SHA-256 digests,
not as recoverable plaintext. Both code paths validate by deriving the digest
from the submitted value and looking it up in the database. Existing login
codes are migrated on deployment; local startup also migrates legacy values.

Approval is not performed by email links or push actions. Those messages only
link to the protected admin page.

## Environment configuration

Set these values in the hosting environment or a local, untracked environment
configuration. The public web-app settings belong in `src/main/resources/application.yml`
as defaults; override them with environment variables for each environment.
Never commit SMTP passwords, Firebase service-account keys, or deployment
credentials.

| Variable | Purpose |
| --- | --- |
| `ADMIN_APPROVER_EMAIL` | `bustrakergithup@gmail.com`, and the email on the approver's ADMIN account |
| `MAIL_USERNAME` | Gmail address used to send notification and invitation emails |
| `MAIL_PASSWORD` | Gmail app password; do not use the normal account password |
| `MAIL_HOST` / `MAIL_PORT` | Defaults to `smtp.gmail.com` / `587` |
| `APP_PUBLIC_BASE_URL` | Public HTTPS origin of Bustraker; used in emails and push notifications |
| `FIREBASE_API_KEY`, `FIREBASE_AUTH_DOMAIN`, `FIREBASE_PROJECT_ID`, `FIREBASE_STORAGE_BUCKET`, `FIREBASE_MESSAGING_SENDER_ID`, `FIREBASE_APP_ID`, `FIREBASE_MEASUREMENT_ID` | Public Firebase web-app settings; defaults match the project provided by the owner |
| `FIREBASE_VAPID_KEY` | Public Web Push certificate key from Firebase Cloud Messaging |
| `FIREBASE_ADMIN_ENABLED` | Set to `true` only after configuring server credentials |
| `FIREBASE_CREDENTIALS_FILE` | Optional `file:` resource for a local service-account JSON kept outside the repository. Leave empty when the host provides Application Default Credentials. |

### Where each value belongs

- The Firebase web configuration (API key, project ID, app ID, and VAPID key)
  is public client configuration. It is exposed by `/api/firebase/config` and
  used by the browser. Firebase API-key restrictions should be configured in
  Google Cloud Console; this key is not an Admin SDK credential.
- `MAIL_USERNAME`, `MAIL_PASSWORD`, and `ADMIN_APPROVER_EMAIL` belong in the
  backend host's secret/environment-variable settings. For the agreed approval
  flow, the sender and approver must be the same Gmail account, and Gmail must
  use an app password with two-step verification enabled.
- On Google Cloud Run, grant the runtime service identity only the Firebase
  Cloud Messaging permission it needs, then set `FIREBASE_ADMIN_ENABLED=true`
  and leave `FIREBASE_CREDENTIALS_FILE` empty. The server uses Application
  Default Credentials; do not download or commit a long-lived service-account
  key for production.
- For local development only, a Firebase service-account JSON may be stored
  outside the repository and referenced with a `file:` URI, for example
  `FIREBASE_CREDENTIALS_FILE=file:///C:/Users/your-user/secure/firebase-admin.json`.
  Do not put the JSON in `src/main/resources`, the web root, or source control.
- `APP_PUBLIC_BASE_URL` must be the externally reachable HTTPS origin used by
  applicants and the administrator. The Firebase web app's `web.app` URL is
  not automatically the origin for this Spring backend.

### Firebase Hosting is not the Spring application host

Bustraker is a Spring Boot application that renders Thymeleaf pages and needs
its Java server, database, sessions, and OAuth callbacks. Firebase Hosting
serves static files; deploying this project with `firebase init` and
`firebase deploy` as if it were a static site will not run the application and
can publish incomplete content. Do not upload the repository or service
credentials to Hosting.

If Firebase Hosting is required as the public domain, deploy the Spring Boot
application separately (for example, to Cloud Run) and configure Hosting to
rewrite requests to that backend. The rewrite requires the actual Cloud Run
service name and region, and the backend also needs its database connection,
OAuth callback configuration, secrets, and migrations. Those deployment values
are intentionally not guessed here. If Hosting is not required, use the
backend host's HTTPS domain directly and configure `APP_PUBLIC_BASE_URL` to it.

The Firebase console commands in the setup screen (`firebase login`,
`firebase init`, `firebase deploy`) are for a selected Hosting deployment. Do
not run `firebase init` at the repository root until choosing the Hosting
rewrite target; it writes deployment files and can alter the intended site
configuration.

Push requires a public HTTPS domain (localhost is suitable for local
development), a compatible browser, and notification permission on the phone.
Sign in as the configured approver on the phone and enable notifications on
`/admin/solicitudes-codigo`.

The browser receives only the Firebase web configuration and its own FCM
registration token. Admin SDK credentials remain server-side. Approval and
rejection are authenticated POST requests protected by Spring Security and
CSRF; a push notification never approves a request by itself.

## Database deployment

The production profile runs the Flyway migration from
`src/main/resources/db/migration/mysql`. Back up the database before deploy and
confirm Flyway can baseline the existing schema. Local/validation H2 schemas
are initialized from `src/test/resources/schema-validation.sql`.

An initial approver account must already exist with the ADMIN role and the
configured approver email. Bootstrap it through the existing
`BOOTSTRAP_ADMIN_*` environment variables; do not create an initial admin
using a public request.
