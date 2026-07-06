@echo off
REM ============================================================
REM  Avvia l'ambiente di test Alexa (ri-lanciabile).
REM   1) BE beatly/api su http://localhost:8080 (docker compose)
REM   2) ngrok http 8080 -> URL pubblico per l'account-linking Alexa
REM  Ogni processo parte in una finestra separata (restano aperti).
REM ============================================================
setlocal
set "BEATLY=%~dp0..\beatly"
REM Dominio statico ngrok (riservato all'account, NON cambia tra i riavvii).
REM E' quello gia' configurato nella console Alexa. Se un giorno cambia, aggiornalo qui.
set "NGROK_DOMAIN=bibliopolar-cadence-unenrolled.ngrok-free.dev"

if not exist "%BEATLY%\docker-compose.yml" (
  echo [ERRORE] Non trovo %BEATLY%\docker-compose.yml
  echo Controlla che il repo beatly sia sibling di ebeat_skill.
  pause
  exit /b 1
)

echo [1/2] Avvio BE beatly (docker compose up api) sulla porta 8080...
start "beatly-api" cmd /k "cd /d "%BEATLY%" && docker compose up api"

echo Attendo qualche secondo che il BE salga...
timeout /t 8 /nobreak >nul

echo [2/2] Avvio ngrok sul dominio statico %NGROK_DOMAIN% ...
start "ngrok" cmd /k "ngrok http --url=https://%NGROK_DOMAIN% 8080"

echo.
echo ============================================================
echo  Ambiente avviato in due finestre:
echo    - beatly-api : http://localhost:8080
echo    - ngrok      : https://%NGROK_DOMAIN%  (URL FISSO, gia' in console Alexa)
echo         Authorization URI : https://%NGROK_DOMAIN%/alexa/oauth/authorize
echo         Access Token URI  : https://%NGROK_DOMAIN%/alexa/oauth/token
echo ============================================================
echo (Chiudi le due finestre per fermare l'ambiente.)
endlocal
