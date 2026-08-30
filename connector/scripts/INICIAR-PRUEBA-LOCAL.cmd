@echo off
setlocal
title GymFort Connector - Prueba local
set "SETUP_SCRIPT=%~dp0setup-local-test.ps1"
if not exist "%SETUP_SCRIPT%" set "SETUP_SCRIPT=%~dp0scripts\setup-local-test.ps1"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SETUP_SCRIPT%"
set EXIT_CODE=%ERRORLEVEL%
echo.
if not "%EXIT_CODE%"=="0" (
  echo El asistente termino con error. Revisa el mensaje anterior.
) else (
  echo El asistente termino correctamente.
)
pause
exit /b %EXIT_CODE%
