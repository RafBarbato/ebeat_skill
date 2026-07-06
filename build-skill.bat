@echo off
REM ============================================================
REM  Builda il jar della skill per l'upload MANUALE sulla Lambda.
REM  Output: target\ebeat-1.0.jar  (shaded, con tutte le dipendenze)
REM ============================================================
setlocal
cd /d "%~dp0"

echo Build in corso (mvn clean package)...
call mvn -q clean package
if errorlevel 1 (
  echo.
  echo [ERRORE] Build fallita. Controlla l'output sopra.
  pause
  exit /b 1
)

echo.
echo ============================================================
echo  Build OK. Carica questo jar sulla Lambda della skill:
echo    %~dp0target\ebeat-1.0.jar
echo.
echo  Ricorda le env var sulla Lambda:
echo    SUPABASE_DB_TRACK_URI, SUPABASE_SERVICE_KEY,
echo    BACKEND_REFRESH_URL, USER_EMAIL
echo ============================================================
endlocal
