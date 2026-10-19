@echo off
setlocal
cd /d "%~dp0"
set "SPRING_PROFILES_ACTIVE=demo"
set "SPRING_DATASOURCE_URL=jdbc:h2:mem:bustraker-demo;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
set "SPRING_DATASOURCE_USERNAME=sa"
set "SPRING_DATASOURCE_PASSWORD="
set "SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver"
set "SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.H2Dialect"
set "SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop"
set "SPRING_FLYWAY_ENABLED=false"
call mvnw.cmd -Dspring-boot.run.profiles=demo spring-boot:run
exit /b %ERRORLEVEL%
