# Test locale — integrazione Alexa (device registry + account-linking)

> Runbook per provare end-to-end il lavoro del branch `feature/alexa_skill`.
> Obiettivo: avviando la riproduzione via Alexa, la skill deve **registrare il
> device** in `alexa_device`, e l'app deve **elencarlo** via `GET /v2/alexa/devices`.
> Include il flusso di account-linking OAuth (necessario per ottenere il token).

## Chi parla con chi (topologia del test)

```
  Alexa cloud ──OAuth (account-linking)──►  ngrok ──►  BE beatly/api (:8080)
       │                                                     │
       │ invoca                                              └─► Supabase (auth admin,
       ▼                                                          alexa_device, current_track)
  Skill Lambda (AWS)  ──REST diretto──►  Supabase
       (AccountService, DeviceService, CurrentTrackService...)

  App beatly ──GET /v2/alexa/devices──►  ngrok / LAN  ──►  BE beatly/api
```

- **Skill a runtime**: NON usa il BE. Va dritta su Supabase (token → email via
  `/auth/v1/admin/users/<token>`, upsert su `alexa_device`, ecc.).
- **BE beatly serve per**: (1) account-linking OAuth (Alexa cloud → BE, via ngrok),
  (2) l'app che legge/gestisce i device (`/v2/alexa/devices`).
- **`BACKEND_REFRESH_URL`** (caso 4, refresh URL YouTube) punta ancora al vecchio
  `be.js` **non migrato** → per QUESTO test è irrilevante (il device registry non
  lo tocca). Basta che `current_track` abbia una traccia con URL valido.

---

## Prerequisiti (una tantum)

- [ ] **Supabase — migration**: eseguire `ebeat_skill.sql` sul SQL editor del
      progetto (idempotente). Verifica che esista la tabella `alexa_device`.
- [ ] **ngrok** installato e autenticato (`ngrok config add-authtoken ...`).
- [ ] **BE** — `api/.env` già presente (ok). Contiene `ALEXA_OAUTH_CLIENT_ID/SECRET`,
      `ALEXA_SKILL_ID=MFP4HI9LYTIIU`, `SUPABASE_*`, DB, Redis.
- [ ] **Skill** — `ebeat_skill/.env` (da `.env.example`): `SUPABASE_DB_TRACK_URI`,
      `SUPABASE_SERVICE_KEY`, `BACKEND_REFRESH_URL`, `USER_EMAIL`.
- [ ] Almeno una riga in `current_track` per l'utente di test con `url` valido
      (non scaduto) e `track_duration` valorizzato — così il play parte.

---

## Passi

### 1+2. Avvia BE + ngrok con un doppio click
```
start-test-env.bat
```
Lo script (root di `ebeat_skill`) apre due finestre:
- **beatly-api** → `docker compose up api` sulla `:8080`;
- **ngrok** → `ngrok http --url=https://<dominio-statico> 8080`.
Chiudere le due finestre ferma l'ambiente. Ri-lanciabile a piacere.
> ✅ Usa il **dominio statico riservato** ngrok
> (`bibliopolar-cadence-unenrolled.ngrok-free.dev`, impostato in cima al `.bat`):
> URL **fisso** tra i riavvii → la console Alexa NON va più ritoccata.
> `ngrok http 8080` senza `--url` darebbe invece un URL casuale (`.ngrok-free.app`)
> diverso ogni volta. ngrok free consente **1 sola sessione agent** alla volta:
> chiudi eventuali ngrok già aperti prima di rilanciare.

### 3. Configura Account Linking sulla Alexa Developer Console
Skill `MFP4HI9LYTIIU` → Build → Account Linking:
- **Authorization URI**: `https://<ngrok>/alexa/oauth/authorize`
- **Access Token URI**: `https://<ngrok>/alexa/oauth/token`
- **Client ID / Secret**: come da `api/.env` (`ALEXA_OAUTH_CLIENT_ID/SECRET`).
- (endpoint BE disponibili: `/authorize`, `/send-otp`, `/verify-otp`, `/token`
  sotto base path `/alexa/oauth`).

### 4. Builda e deploya la skill (Lambda) — upload manuale del jar
```
build-skill.bat               # mvn clean package -> target\ebeat-1.0.jar
```
Poi **carica manualmente** `target\ebeat-1.0.jar` nella console AWS Lambda della
skill. Verifica le env var: `SUPABASE_DB_TRACK_URI`, `SUPABASE_SERVICE_KEY`,
`BACKEND_REFRESH_URL`, `USER_EMAIL`.

### 5. Collega l'account e prova
1. App Alexa (o Developer Console → Test) → abilita la skill → **Link Account** →
   completa OTP/login (flusso OAuth verso il BE via ngrok).
2. *"Alexa, apri ebeat"* (o *"metti in play"*) → la skill risolve email,
   registra il device e avvia la traccia da `current_track`.

### 6. App: leggi i device (opzionale ma parte del flusso)
- Punta `EBEAT_API` dell'app all'URL ngrok (o IP LAN del PC) se provi su device fisico.
- Apri il menu "dispositivi audio" → l'hook `useAlexaDevices` chiama
  `GET /v2/alexa/devices` e deve mostrare l'Echo appena usato.

---

## Verifiche (come capire se funziona)

- [ ] **Supabase**: `select * from alexa_device where user_id = '<email>';` →
      riga con `device_id` dell'Echo, `last_seen_at` recente.
- [ ] **Log skill (CloudWatch)**: nessun `WARN "Registrazione device fallita"`.
- [ ] **BE (log ngrok / console docker)**: hit su `/alexa/oauth/token` (linking) e
      su `/v2/alexa/devices` (app).
- [ ] **App**: il device compare nel menu, rinominabile (PATCH) e rimovibile (DELETE).

---

## Script di supporto (root `ebeat_skill`)

- **`start-test-env.bat`** — avvia BE (`docker compose up api`) + `ngrok http 8080`
  in due finestre separate. Ri-lanciabile.
- **`build-skill.bat`** — `mvn clean package` → `target\ebeat-1.0.jar` (da caricare
  a mano sulla Lambda).

## Punti aperti / rischi

1. **ngrok URL volatile** (piano free): riallineare la console Alexa a ogni restart.
2. **BE boot**: l'`api` richiede Redis (rate-limiter + cache fail-closed OAuth).
   `api/.env` NON ha `REDIS_*` → il `docker-compose.yml` ora include un servizio
   `redis` locale (standalone, no TLS/auth) e imposta `REDIS_HOST=redis` sull'api.
   Verificato: "Redis connected/ready", server su `:8080`, rotte Alexa vive
   (`/alexa/oauth/authorize` → 400, `/v2/alexa/devices` → 401). DB Postgres:
   punta al Supabase remoto via `api/.env` (nessun Postgres locale necessario).
3. **`current_track` valida**: senza una traccia con URL non scaduto il play non
   parte e non si arriva alla registrazione device (che è dentro `PlaybackStarter`).
