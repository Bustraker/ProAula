@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "& { . '.\scripts\setup-local-oauth-https.ps1'; if (-not $?) { exit 1 }; & '.\mvnw.cmd' spring-boot:run; exit $LASTEXITCODE }"
exit /b %ERRORLEVEL%
