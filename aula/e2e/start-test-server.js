const { spawn } = require("node:child_process");
const path = require("node:path");

const projectDirectory = path.resolve(__dirname, "..");
const wrapper = path.join(projectDirectory, "mvnw.cmd");
const command = `"${wrapper}" -DskipTests -Dspring-boot.run.useTestClasspath=true -Dspring-boot.run.profiles=validation test-compile spring-boot:run`;
const server = spawn(command, [], {
  shell: true,
  cwd: projectDirectory,
  env: {
    ...process.env,
    JWT_SECRET: "validation-only-key-with-more-than-32-characters",
    SPRING_FLYWAY_ENABLED: "false",
    SPRING_DATASOURCE_URL: "jdbc:h2:mem:bustraker-e2e;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    SPRING_DATASOURCE_USERNAME: "sa",
    SPRING_DATASOURCE_PASSWORD: "",
    SPRING_DATASOURCE_DRIVER_CLASS_NAME: "org.h2.Driver",
    SPRING_JPA_DATABASE_PLATFORM: "org.hibernate.dialect.H2Dialect",
    SPRING_JPA_HIBERNATE_DDL_AUTO: "create-drop",
    SERVER_PORT: "3466"
  },
  stdio: "inherit"
});

server.on("error", error => {
  console.error("Could not start the validation server:", error);
  process.exitCode = 1;
});

server.on("exit", (code, signal) => {
  if (signal) {
    console.error(`Validation server stopped by signal ${signal}`);
  }
  process.exitCode = code ?? 1;
});
