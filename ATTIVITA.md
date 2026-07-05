# Tracciamento attività — ebeat skill

> Log operativo delle attività. Lo stato di dettaglio dei casi d'uso vive in
> `CLAUDE.md` (sezione *Casi d'uso*); qui si tiene il **registro cronologico**
> del lavoro (fatto / in corso / da fare) per non perdere il filo tra sessioni.
>
> Legenda stato: ✅ Fatto · 🟡 In corso · 🔵 Da fare · ⏸️ Parcheggiato

---

## Architettura

### Quadro d'insieme
Tre attori indipendenti che comunicano **solo via Supabase** (nessuna chiamata
diretta tra loro):

```
  App beatly (mobile)              Supabase (Postgres + Realtime)          Skill Alexa (Lambda Java)
  ───────────────────              ──────────────────────────────          ─────────────────────────
  IP residenziale utente           current_track  (stato riproduzione)     IP datacenter AWS
  YouTube/InnerTube OK      ──►     playback_queue (coda precaricata)  ◄──  NON chiama YouTube/Deezer
  risolve url + coda               alexa_device   (registry device)        legge/scrive stato, emette
  scrive stato playback            (service key, RLS on)                    AudioPlayer directives
                                          ▲
                                          │
                            BE Node (alexa-supabase-backend)
                            /refresh-track (caso 4, yt-dlp android_vr)
                            /resolve-*, /refill-* → 410 Gone (deprecati)
```

**Perché così**: YouTube fa rate-limiting/bot-detection sugli IP datacenter, quindi
solo l'app (IP residenziale) può parlare con YouTube. Skill e BE **non** chiamano
YouTube. Supabase è lo spazio condiviso; l'app pre-risolve URL stream + coda, la
skill consuma e riproduce. (Vincoli #1–#3 in `CLAUDE.md`.)

### Skill Alexa — struttura codice (`lambda/custom/`)
- **`EbeatStreamHandler.java`** — entry point Lambda (`SkillStreamHandler`),
  registra tutti i request/exception handler.
- **`handlers/`** — un handler per tipo di richiesta Alexa:
  - Vocali: `LaunchHandler`, `MusicPlayIntentHandler` (play/resume/startover/loop/sync),
    `NextIntentHandler`, `PauseIntentHandler`, `CancelAndStopIntentHandler`,
    `PlayTrackIntentHandler` + `YesIntentHandler`/`NoIntentHandler` (ricerca vocale,
    caso 15 — **disabilitato**), `Help`/`Fallback`.
  - Eventi AudioPlayer: `PlaybackNearlyFinishedHandler` (enqueue coda / loop),
    `PlaybackStoppedHandler` (salva offset), `PlaybackFailedHandler` (retry),
    `PlaybackEventLogHandler`, `SessionEndedHandler`.
    (`PlaybackStartedHandler` esiste ma **non registrato**: refill server-side deprecato.)
- **`service/`** — accesso a stato esterno via `RestClient` (Spring 6.1):
  - `AccountService` — `accessToken` → email utente (API admin Supabase).
  - `CurrentTrackService` — CRUD su `current_track` (findByUserId, updateOffset, promote…).
  - `PlaybackQueueService` — legge/consuma `playback_queue` (caso 7).
  - `DeviceService` — upsert `alexa_device` (registry device, **in corso**).
  - `RefreshService` — POST `/refresh-track` al BE per URL scaduti (caso 4).
  - **Legacy/disabilitati**: `DeezerService`, `YoutubeResolveService`, `RefillService`.
- **`util/`** — POJO e infra: `CurrentTrack`, `QueueItem`, `DeezerTrack` (modelli),
  `PlaybackStarter` (logica condivisa "risolvi utente → traccia → refresh → Play"),
  `SupabaseRestClient` (factory RestClient con header `apikey` + `Authorization: Bearer`).

### Flusso tipico (avvio riproduzione)
`LaunchHandler` / `MusicPlayIntentHandler` → `PlaybackStarter.start()`:
1. `AccountService.resolveEmail(accessToken)` → email.
2. `DeviceService.registerDevice(email, deviceId)` — best-effort (in corso).
3. `CurrentTrackService.findByUserId(email)` → traccia corrente.
4. Se URL scaduto → `RefreshService.refresh()` (BE `/refresh-track`).
5. Emette `AudioPlayer.Play REPLACE_ALL` con offset salvato (clamp su `track_duration`).

### Stato condiviso (Supabase) — vedi schema `ebeat_skill.sql`
- **`current_track`** — 1 riga/utente (UPSERT su `user_id`): url + expires, offset,
  metadati traccia, `loop_mode`, `active_device`, `is_playing`. Fonte di verità del playback.
- **`playback_queue`** — coda tracce successive precaricate dall'app (PK `user_id,position`).
- **`alexa_device`** — registry device Alexa per utente (**nuova, in corso**).
- `ebeat_skill.sql` (root) è lo schema **canonico**: idempotente, ri-eseguibile.

### Repo esterni collegati
- **App**: `C:\Users\Raffaele\IdeaProjects\beatly\app` (React Native, YouTube/InnerTube).
- **BE Node**: `alexa-supabase-backend` (`be.js`) — solo `/refresh-track` vivo; il resto 410 Gone.

### Config (env var skill)
`SUPABASE_DB_TRACK_URI`, `SUPABASE_SERVICE_KEY`, `USER_EMAIL`, `BACKEND_REFRESH_URL`
(vedi `.env.example`). Build Maven (`pom.xml`, Java 21). Modello skill in `models/it.json`.

---

## In corso

### Device registry Alexa (propedeutico casi 12 e 13)
- **Stato**: 🟡 In corso — codice scritto, **non committato**, **non verificato**.
- **Obiettivo**: la skill registra ad ogni invocazione il device Alexa corrente
  su Supabase, così l'app può elencarlo nel menu "dispositivi" e sceglierlo come
  target per il trasferimento ascolto.
- **Fatto**:
  - `ebeat_skill.sql` — nuova tabella `alexa_device (user_id, device_id, name,
    created_at, last_seen_at)`, PK `(user_id, device_id)`, indice su `user_id`,
    RLS abilitata.
  - `lambda/custom/service/DeviceService.java` (nuovo) — upsert best-effort su
    `alexa_device` (`Prefer: resolution=merge-duplicates`, aggiorna solo
    `last_seen_at`, non tocca `name`). Deriva base URL + service key da
    `SUPABASE_DB_TRACK_URI`.
  - `lambda/custom/util/PlaybackStarter.java` — chiama
    `DeviceService.registerDevice(email, deviceId)` ad ogni avvio riproduzione,
    best-effort (errore non blocca il play). `deviceId` da `System.device.deviceId`.
- **Lato BE/app — già FATTO** (in `beatly/api`, non in questo repo):
  - ✅ Endpoint lettura/gestione: `GET/PATCH/DELETE /v2/alexa/devices`
    (`Alexa.controller.ts`, `Alexa.routes.ts`, `AlexaDevice.repository.ts`).
  - ✅ App consuma il registry via hook `useAlexaDevices.ts` (menu dispositivi).
  - ✅ Account-linking OAuth Alexa migrato in `beatly/api` (`AlexaOAuth.*`).
- **Da fare (solo lato skill, questo repo)**:
  - 🔵 Verifica end-to-end (build + prova che l'upsert `DeviceService` arrivi su Supabase).
  - 🔵 Commit del lavoro (skill: `DeviceService` + `PlaybackStarter` + `ebeat_skill.sql`).
  - 🔵 Scelta target device per il trasferimento (caso 13) — consumo del registry.

---

## Da fare / aperti

- 🔵 **Caso 6** — sync traccia attiva app → Supabase (lato app beatly).
- 🔵 **Caso 12** — mutua esclusione device (single active player). Migration
  `active_device` / `is_playing` / `playback_state_changed_at` già in schema;
  manca la scrittura coerente lato skill + subscription Realtime lato app.
- 🔵 **Caso 13** — trasferimento ascolto app → Alexa (non mandatorio). Livello A
  (handover assistito) è la strada consigliata.
- ⏸️ **Caso 14** — sync live durante playback Alexa (workaround `SyncIntent`).
- ⏸️ **Caso 15** — ricerca vocale: **disabilitata dal 2026-05-12** (contraddice
  il vincolo "no chiamate YouTube da BE/skill"). Re-design opzione B via Realtime.
- 🟡 **Caso 10 / 11** — sync app → Alexa: parziale, coperto solo lo scenario
  "skill ferma".

---

## Igiene repo

- ⚠️ `Lucio Dalla - Canzone (Videoclip) [VkTNnCCKnE4].webm` (16 MB) presente in
  root, non tracciato. **Viola il vincolo #1** (no download/storage audio) e non
  va committato → da rimuovere e/o aggiungere a `.gitignore`.

---

## Log cronologico

- **2026-07-05** — Creato questo file di tracciamento. Rilevato lavoro in corso
  sul device registry (`alexa_device` + `DeviceService`), non ancora committato.
  Segnalato il `.webm` da rimuovere.
