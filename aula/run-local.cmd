@echo off
setlocal
cd /d "%~dp0"
set "SPRING_PROFILES_ACTIVE=local"
call mvnw.cmd spring-boot:run
exit /b %ERRORLEVEL%
