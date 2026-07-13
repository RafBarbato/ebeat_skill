@echo off
REM ============================================================
REM  Builda il jar della skill per l'upload MANUALE sulla Lambda.
REM  Output: target\ebeat-1.0.jar  (shaded, con tutte le dipendenze)
REM ============================================================
setlocal
cd /d "%~dp0"

REM Il pom targetta release 21: se il JAVA_HOME di sistema non e' 21 il build
REM fallisce con "invalid target release: 21". Se e' presente il JDK 21 di
REM JetBrains lo forziamo qui, cosi' il build e' ripetibile a prescindere.
if exist "%USERPROFILE%\.jdks\dragonwell-ex-21.0.10\bin\javac.exe" (
  set "JAVA_HOME=%USERPROFILE%\.jdks\dragonwell-ex-21.0.10"
  echo Uso JDK 21: %USERPROFILE%\.jdks\dragonwell-ex-21.0.10
)

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
echo    SKILL_BE_INTERNAL_URL  (es. https://host/v1/internal/alexa)
echo    SKILL_BE_SECRET        (bearer condiviso col BE)
echo    (SUPABASE_* non piu' necessarie: Fase 5, resolveEmail via BE)
echo    BACKEND_REFRESH_URL, USER_EMAIL
echo ============================================================
endlocal
