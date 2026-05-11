# ebeat skill — appunti di progetto

## Vincoli di progetto

**Non scaricare né conservare i file audio.** Per ragioni
legali/copyright, il backend e la skill **non devono** salvare bytes
audio (m4a, mp3, ecc.) su disco, bucket o database. Sono consentiti
solo:
- URL stream "live" forniti direttamente da YouTube (es. `googlevideo.com/videoplayback?...`).
- Metadati (titolo, artista, durata, ID di servizio, scadenza URL).
- Le URL possono essere persistite in Supabase finché valide, ma
  **non il contenuto audio sottostante**.

**Conseguenza pratica**: la pipeline `/refresh-track-storage` su
`be.js` (che fa download + upload del file su Supabase Storage bucket
`audio-cache`) **non va usata in produzione**. Resta nel codice solo
come riferimento storico; il flusso live è `/refresh-track` (yt-dlp
android_vr → URL googlevideo passato direttamente ad Alexa). Quando
yt-dlp viene rate-limited o bloccato da YouTube, la strada giusta è
risolvere il blocco a monte (aggiornare yt-dlp, ruotare IP, usare
`youtubei.js` con PoT che restituisce URL googlevideo), **non**
fare il download del file.

## Regola: schema SQL canonico in `ebeat_skill.sql`

**Ogni modifica allo schema Supabase del progetto va rispecchiata in
`ebeat_skill.sql`** (root del repo). È il file fonte di verità: idempotente
(usa `IF NOT EXISTS` / blocchi `DO $$ ... END $$` con guard) e ri-eseguibile
in sicurezza sul SQL editor di Supabase. Quando si introduce una nuova
colonna, tabella, indice, publication Realtime o policy RLS, va aggiunta
qui prima/insieme al codice che la usa, così da non avere divergenza tra
documentazione e DB reale.

## Sincronizzazione traccia corrente: beatly → Supabase

### Obiettivo
Scrivere in tempo reale lo stato di riproduzione su Supabase (tabella `current_track`)
in modo che la skill Alexa possa riprendere l'ascolto dal punto esatto in cui l'utente
si è fermato sull'app mobile.

### Tabella Supabase: `current_track`

| Campo                       | Tipo        | Descrizione                                                                          |
|-----------------------------|-------------|--------------------------------------------------------------------------------------|
| `user_id`                   | TEXT        | Email dell'utente (chiave di ricerca)                                                |
| `url`                       | TEXT        | URL stream YouTube                                                                   |
| `url_expires_at`            | TIMESTAMPTZ | Scadenza URL (da `expireAt` della cache)                                             |
| `offset`                    | BIGINT      | Posizione in millisecondi                                                            |
| `track_id`                  | BIGINT      | ID Deezer della traccia                                                              |
| `track_title`               | TEXT        | Titolo                                                                               |
| `track_artist`              | TEXT        | Artista                                                                              |
| `track_duration`            | BIGINT      | Durata traccia in **secondi**, usata per clamp dell'offset                           |
| `youtube_id`                | TEXT        | ID YouTube della traccia (usato per refresh URL)                                     |
| `loop_mode`                 | BOOLEAN     | Riproduzione in loop attiva (caso 9)                                                 |
| `active_device`             | TEXT        | Device attivo: `alexa:<deviceId>`, `app:<installationId>`, `NULL` se nessuno (caso 12) |
| `is_playing`                | BOOLEAN     | `true` se sta suonando, `false` se in pausa/stop (casi 11/12)                        |
| `playback_state_changed_at` | TIMESTAMPTZ | Timestamp ultimo cambio di `active_device` o `is_playing`                            |
| `updated_at`                | TIMESTAMPTZ | Ultimo aggiornamento                                                                 |

La riga è unica per utente (UPSERT su `user_id`).

### Tabella Supabase: `playback_queue` (caso 7)

Coda delle tracce successive precaricate dall'app. Permette skip multipli
senza re-fetch. Riga per `(user_id, position)`; `position = 1` è la prossima
traccia dopo quella in `current_track`.

| Campo            | Tipo        | Descrizione                                                  |
|------------------|-------------|--------------------------------------------------------------|
| `user_id`        | TEXT        | Email dell'utente (FK logica verso `current_track.user_id`)  |
| `position`       | INTEGER     | Posizione nella coda (1, 2, 3, …)                            |
| `youtube_id`     | TEXT        | ID YouTube                                                   |
| `url`            | TEXT        | URL stream pre-risolto (opzionale, l'app può lasciare NULL)  |
| `url_expires_at` | TIMESTAMPTZ | Scadenza URL                                                 |
| `track_id`       | BIGINT      | ID Deezer                                                    |
| `track_title`    | TEXT        | Titolo                                                       |
| `track_artist`   | TEXT        | Artista                                                      |
| `track_duration` | BIGINT      | Durata in secondi                                            |
| `added_at`       | TIMESTAMPTZ | Timestamp inserimento                                        |

PK: `(user_id, position)`. Indice secondario su `user_id`.

### Dove intervenire in beatly

#### 1. Cambio traccia — scrivere url + metadati
**File:** `app/src/services/playback.service.ts`
**Evento:** `Event.PlaybackActiveTrackChanged`

Quando parte una nuova traccia, eseguire UPSERT con tutti i campi:
- `url` e `url_expires_at` dalla cache della traccia (campo `expireAt`)
- `track_id`, `track_title`, `track_artist` dall'oggetto track
- `track_duration` (secondi, intero) — **importante**: la skill clamper l'offset
  alla durata, se manca il clamp non si attiva e si rischia un seek oltre la
  fine del file (Alexa risponde con `MEDIA_ERROR_SERVICE_UNAVAILABLE`).
- `offset = 0`
- `updated_at = now()`

#### 2. Aggiornamento offset periodico
**File:** `app/src/services/playback.service.ts`
**Evento:** `Event.PlaybackProgressUpdated` (ogni 5 secondi)

Eseguire UPSERT con solo:
- `offset` (posizione corrente in ms)
- `updated_at = now()`

#### 3. Pausa / Stop
**File:** `app/src/services/playback.service.ts`
**Evento:** `Event.PlaybackState` (quando stato diventa PAUSED o STOPPED)

Eseguire UPSERT con:
- `offset` finale
- `updated_at = now()`

### Chiamata Supabase (TypeScript)

```typescript
await supabase
  .from('current_track')
  .upsert({
    user_id: userEmail,
    url: track.url,
    url_expires_at: track.expireAt,
    offset: Math.floor(position * 1000), // secondi → millisecondi
    track_id: track.provider.track.id,
    track_title: track.title,
    track_artist: track.artist,
    track_duration: track.duration, // secondi (intero)
    updated_at: new Date().toISOString(),
  }, { onConflict: 'user_id' });
```

### Note
- L'aggiornamento dell'offset ogni 5s genera molte scritture: valutare di ridurre
  la frequenza a 15-30s se il traffico su Supabase diventa eccessivo.
- Gli URL YouTube scadono (tipicamente dopo alcune ore). Se Alexa trova
  `url_expires_at` nel passato, avvisa l'utente di riaprire l'app.
- `position` da `Event.PlaybackProgressUpdated` è in secondi (float),
  la skill Alexa si aspetta l'offset in millisecondi.

---

## Casi d'uso

| #  | Caso                                                | Stato            |
|----|-----------------------------------------------------|------------------|
| 1  | Avvio skill da comando vocale (auto-play)           | Fatto            |
| 2  | Stop della skill                                    | Fatto            |
| 3  | Avvio ultima traccia attiva al momento              | Fatto            |
| 4  | Avvio ultima traccia attiva dopo scadenza URL       | Fatto            |
| 5  | Stop della musica                                   | Fatto            |
| 6  | Aggiornamento traccia attiva su Supabase            | Da fare          |
| 7  | Passaggio a traccia successiva (playlist)           | Fatto            |
| 8  | Riavvio della traccia da capo                       | Fatto            |
| 9  | Riproduzione in loop                                | Fatto            |
| 10 | Cambio traccia dall'app (skip manuale utente)       | Parziale         |
| 11 | Play / Pause / Stop dall'app riflessi su Alexa      | Parziale         |
| 12 | Mutua esclusione device (single active player)      | Da fare          |
| 13 | Trasferimento ascolto app → Alexa (non mandatorio)  | Da fare          |
| 14 | Sync live durante riproduzione Alexa (non mandatorio)| Workaround       |
| 15 | Ricerca traccia via comando vocale (Deezer + YouTube)| Fatto            |

---

### 1. Avvio skill da comando vocale (auto-play)
- **Stato**: Fatto.
- **Trigger**: utente dice *"Alexa, apri ebeat"*
- **Tipo richiesta**: `LaunchRequest`
- **Componente**: `LaunchHandler` → delega a `PlaybackStarter.start(input, Mode.START)`.
- **Flusso**: identico al caso 3 (Avvio ultima traccia attiva). Nessun
  benvenuto interlocutorio: la skill risponde direttamente con la directive
  `AudioPlayer.Play` sull'ultima traccia in `current_track`. Se l'URL è
  scaduto fa refresh (caso 4). Se l'utente non ha account collegato o
  nessuna traccia, risponde con messaggio appropriato e chiude la sessione.
- **Risposta**: speech `"Riproduco <titolo> di <artista> da ebeat."` +
  directive `Play REPLACE_ALL` con offset salvato.
- **Stato sessione**: chiusa (`withShouldEndSession(true)`); l'audio
  continua nel contesto AudioPlayer.
- **Refactor**: la logica di "risolvi utente → leggi traccia → refresh
  URL → emetti Play" è in `util/PlaybackStarter.java`, condivisa con
  `MusicPlayIntentHandler` (gestisce anche StartOver, LoopOn, Resume).

---

### 2. Stop della skill
- **Stato**: Fatto.
- **Trigger**: utente dice *"Alexa, esci"* / *"Alexa, stop"* mentre la skill è aperta senza audio attivo.
- **Tipo richiesta**: `IntentRequest` con `AMAZON.StopIntent` o `AMAZON.CancelIntent`.
- **Componente**: `CancelAndStopIntentHandler`.
- **Flusso**: nessuna chiamata esterna, semplice chiusura della sessione.
- **Risposta**: saluto vocale + `withShouldEndSession(true)`.

---

### 3. Avvio ultima traccia attiva al momento
- **Stato**: Fatto.
- **Trigger**: utente dice *"Alexa, chiedi a ebeat di mettere in play"* (o equivalente che fa scattare il `MusicPlayIntent`).
- **Tipo richiesta**: `IntentRequest` con `MusicPlayIntent`.
- **Componente**: `MusicPlayIntentHandler` + `AccountService` + `CurrentTrackService`.
- **Flusso**:
  1. Recupera `accessToken` dall'account linking — se assente, mostra `LinkAccountCard`.
  2. `AccountService.resolveEmail(accessToken)` → email utente via API admin Supabase.
  3. `CurrentTrackService.findByUserId(email)` → riga corrente in `current_track`.
  4. Se la traccia non esiste, risposta vocale che invita ad avviare la riproduzione dall'app.
  5. Se l'URL è valido, emetti `AudioPlayer.Play` con `PlayBehavior.REPLACE_ALL`, `offset` in ms e URL stream.
- **Risposta**: speech con titolo/artista + directive di riproduzione + sessione chiusa.

---

### 4. Avvio ultima traccia attiva dopo scadenza URL YouTube
- **Stato**: Fatto.
- **Trigger**: stesso del caso 3, ma `current_track.url_expires_at` è nel passato.
- **Componenti**: `MusicPlayIntentHandler` + `RefreshService` (skill) + endpoint `/refresh-track` su `alexa-supabase-backend` (Node.js).
- **Flusso**:
  1. Skill rileva URL scaduto via `CurrentTrack.isExpired()`.
  2. `RefreshService.refresh(userId, youtubeId)` → POST `/refresh-track` con `Authorization: Bearer <SUPABASE_SERVICE_KEY>`.
  3. Backend esegue `yt-dlp -j -f bestaudio[ext=m4a] ...`, parsa l'URL e la scadenza, fa UPSERT su `current_track`.
  4. Skill rilegge la traccia da Supabase e riproduce.
- **Pre-requisito**: il campo `youtube_id` deve essere stato popolato (dallo script `push_track.py` o dall'app beatly).
- **Env var skill**: `BACKEND_REFRESH_URL` (es. `https://<host>/refresh-track`).
- **Limitazioni**: il backend Node deve essere raggiungibile (per dev su ngrok, per prod hostato altrove).

---

### 5. Stop / Pausa / Ripresa della musica
- **Stato**: Fatto.
- **Tre comandi distinti**, ciascuno con handler dedicato:

  **Stop** — *"Alexa, stop"* / *"esci"* (`AMAZON.StopIntent`, `AMAZON.CancelIntent`)
  - Componente: `CancelAndStopIntentHandler`.
  - Emette `AudioPlayer.Stop` + speech "A presto!" + `withShouldEndSession(true)`.
  - Disattiva `loop_mode`.
  - La skill esce; per riprendere serve un nuovo *"Alexa, apri ebeat"*.

  **Pausa** — *"Alexa, pausa"* / *"metti in pausa"* (`AMAZON.PauseIntent`)
  - Componente: `PauseIntentHandler` (handler dedicato, separato da Stop).
  - Emette solo `AudioPlayer.Stop` directive: nessun speech, nessuna chiusura.
  - Alexa invia `AudioPlayer.PlaybackStopped` → `PlaybackStoppedHandler`
    persiste l'offset su `current_track`.
  - L'utente può poi dire *"Alexa, riprendi"* senza aprire di nuovo la skill.

  **Ripresa** — *"Alexa, riprendi"* / *"continua"* (`AMAZON.ResumeIntent`)
  - Componente: `MusicPlayIntentHandler` (`canHandle` accetta anche
    `AMAZON.ResumeIntent`).
  - Legge `current_track.offset`, refresh URL se scaduto, emette `AudioPlayer.Play`
    con `REPLACE_ALL` e l'offset salvato.
  - Speech: "Riprendo." (corto, non invasivo).

- **Flusso comune Pause→Resume**:
  1. Utente: *"Alexa, pausa"* → `PauseIntentHandler.handle()` → `Stop` directive.
  2. Alexa: `AudioPlayer.PlaybackStopped(offsetInMilliseconds=...)`.
  3. `PlaybackStoppedHandler` salva `offset` su `current_track`.
  4. Utente: *"Alexa, riprendi"* → `MusicPlayIntentHandler` → legge offset →
     `Play REPLACE_ALL` da quel punto.

- **Pre-requisito skill model**: `AMAZON.PauseIntent` e `AMAZON.ResumeIntent`
  aggiunti al modello sulla Developer Console (sono built-in standard, di
  solito già attivi).

- **Bug storico (corretto 2026-05-03)**: `CancelAndStopIntentHandler`
  intercettava anche `AMAZON.PauseIntent` e applicava `withShouldEndSession(true)`
  → la skill usciva dopo "pausa" e *"riprendi"* non aveva handler. Fix:
  separato `PauseIntentHandler`, aggiunto `AMAZON.ResumeIntent` al
  `MusicPlayIntentHandler`.

---

### 6. Aggiornamento traccia attiva su spazio condiviso (Supabase)
- **Stato**: Da fare (lato app beatly).
- **Trigger**: scrittura **dall'app beatly** durante la riproduzione (non dalla skill).
- **Componente**: `playback.service.ts` lato app mobile.
- **Eventi mobile**:
  - `Event.PlaybackActiveTrackChanged` → UPSERT completo (url, expires_at, track_id, title, artist, offset = 0).
  - `Event.PlaybackProgressUpdated` (ogni 5–30s) → UPSERT solo di `offset` + `updated_at`.
  - `Event.PlaybackState` su PAUSED/STOPPED → UPSERT con offset finale.
- **Risultato atteso**: la skill Alexa, quando interrogata, trova sempre lo stato più aggiornato.
- **Riferimento**: vedi sezione *Sincronizzazione traccia corrente* in cima a questo documento.

---

### 7. Passaggio a traccia successiva rispetto alla playlist salvata in app
- **Stato**: Fatto.
- **Trigger vocale**: utente dice *"Alexa, prossima"* / *"avanti"* / *"salta"* durante la riproduzione.
- **Trigger automatico**: fine della traccia corrente (`AudioPlayer.PlaybackNearlyFinished` / `PlaybackFinished`).
- **Tipo richiesta**: `IntentRequest` con `AMAZON.NextIntent` **oppure** `AudioPlayer.PlaybackNearlyFinished`.
- **Componente**: `PlaybackNearlyFinishedHandler` (auto, ENQUEUE) + `NextIntentHandler` (vocale, REPLACE_ALL).
- **Pre-requisito skill model**: `AMAZON.NextIntent` aggiunto al modello (built-in, niente sample necessari).
- **Schema scelto**: tabella separata `playback_queue` (vedi sezione in
  cima a questo documento). L'app riempie la coda con N tracce successive
  (es. N=3-5) al `PlaybackActiveTrackChanged`. La skill, alla richiesta di
  skip, legge la riga con `position=1`, la promuove in `current_track` e
  cancella la riga (l'app rifornirà la coda al prossimo
  `PlaybackActiveTrackChanged`).
- **Flusso (transizione automatica, implementato in `PlaybackNearlyFinishedHandler`)**:
  1. `PlaybackNearlyFinishedHandler` riceve l'evento ~10s prima della fine.
  2. `PlaybackQueueService.findNext(email)` legge la riga con `position` minima.
  3. `CurrentTrackService.promoteFromQueue(email, item)`: PATCH su
     `current_track` con i metadati della coda + `offset = 0`.
  4. `PlaybackQueueService.delete(email, item.position)` cancella la riga
     promossa (best-effort, le posizioni rimanenti restano come "buchi"
     finché l'app non rifornisce — non rinumera).
  5. Se `url` è null o scaduto su current_track, chiama `refreshService.refresh()`.
  6. Emette `AudioPlayer.Play` con `PlayBehavior.ENQUEUE`,
     `expectedPreviousToken = currentToken`, e l'URL nuovo.
- **Flusso (intent vocale `AMAZON.NextIntent`, da implementare)**:
  Identico ma con `PlayBehavior.REPLACE_ALL` (interrompe la traccia
  corrente). Nuovo `NextIntentHandler` da aggiungere in
  `EbeatStreamHandler`.
- **Decisione di pre-caricamento URL**: lato app (`playback.service.ts`),
  più semplice — l'app conosce già le tracce successive perché è la stessa
  che ha creato la playlist in memoria. Per le tracce dove l'app non ha
  ancora un URL fresco, può salvare solo `youtube_id` (e metadati) e la
  skill chiamerà `/refresh-track` come per il caso 4.

---

### 8. Riavvio della traccia da capo
- **Stato**: Fatto.
- **Trigger**: utente dice *"Alexa, metti da capo"* / *"ricomincia"* / *"start over"*.
- **Tipo richiesta**: `IntentRequest` con `AMAZON.StartOverIntent`.
- **Componente**: `MusicPlayIntentHandler` (gestisce sia `MusicPlayIntent` sia `AMAZON.StartOverIntent`).
- **Flusso**:
  1. Risolve l'utente come nel caso 3.
  2. Recupera la traccia corrente (e fa refresh se l'URL è scaduto).
  3. Forza `offset = 0` e fa PATCH su `current_track.offset` tramite `CurrentTrackService.updateOffset()`.
  4. Emette `AudioPlayer.Play` con `PlayBehavior.REPLACE_ALL` e `offset = 0`.
- **Risposta**: speech *"Riavvio &lt;titolo&gt; da capo."* + directive di riproduzione.
- **Pre-requisito skill model**: `AMAZON.StartOverIntent` aggiunto agli intent della skill nella Alexa Developer Console.

---

## Prossimi passi (estratti dall'analisi dell'app beatly)

L'app `beatly` (`C:\Users\Raffaele\IdeaProjects\beatly\app`) riesce a riprodurre
senza blocchi grazie a 5 livelli che la skill Alexa oggi non ha. Ordinati per
priorità di porting:

### A. Risoluzione URL multi-strategia / multi-client (priorità ALTA)
- **Riferimento app**: `src/services/innertube/streaming.service.ts`
  → `resolveStreamData` cicla su 4 strategie (`track` → `cache` → `music` →
  `video`) e per ogni ID tenta più client InnerTube via
  `resolveWithClientStrategies`.
- **Stato skill**: il backend Node `/refresh-track` usa **yt-dlp single-shot**.
  Se yt-dlp è bloccato/limitato/stale → URL "ok" ma throttled o invalido →
  Alexa rimane in buffering o si interrompe.
- **Vincolo PO Token (scoperto 2026-05-02)**: da fine 2024 YouTube richiede un
  GVS PO Token per i client `android`/`ios`/`mweb`. Senza token, yt-dlp scarta
  *tutti* i formati audio HTTPS di quei client. Il client `web` espone solo
  opus/webm sui video musicali (Alexa non li supporta). L'unico client che a
  oggi espone ancora i formati 140/139 (m4a/AAC) senza PO Token è
  **`android_vr`**. Soluzione attuale in `be.js`: cascata
  `android_vr → web_safari → web` con format selector
  `140/139/bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio[protocol^=m3u8]`.
- **Vincolo IP locking googlevideo (verificato 2026-05-02, NON bloccante per
  Alexa)**: le URL googlevideo includono `ip=...` nei `sparams` (parametri
  firmati). In teoria solo chi le ha richieste dovrebbe poterle consumare. In
  pratica, **per il client `c=ANDROID_VR` Alexa riproduce comunque** anche se
  l'IP è diverso. Verificato empiricamente con A/B test: stesso URL servito
  ad Alexa, riproduzione OK. Causa probabile: il CDN googlevideo è meno
  rigido sui client di tipo "TV/VR" perché normalmente il device che ottiene
  l'URL e quello che lo consuma possono divergere (es. cast).
- **Bug originale che aveva fuorviato la diagnosi (risolto 2026-05-02)**:
  `current_track.offset = 400000` ms su un file di 271 s → seek oltre fine
  → Alexa risponde `MEDIA_ERROR_SERVICE_UNAVAILABLE`. Stesso codice di errore
  che ci aveva fatto sospettare l'IP locking. Fix: aggiunto `track_duration`
  in `current_track` e clamp dell'offset in
  `MusicPlayIntentHandler`/`PlaybackFailedHandler` se `offset >= (duration-2)*1000`.
- **Architettura adottata 2026-05-02**:
  - **Default**: endpoint `/refresh-track` nel BE Node usa
    `yt-dlp -j --extractor-args youtube:player_client=android_vr` e salva
    direttamente l'URL googlevideo in `current_track.url`. Semplice, veloce,
    nessun upload, nessun PoT, nessun bucket. Funzionante con Alexa.
  - **Backup**: endpoint `/refresh-track-storage` mantiene la pipeline
    youtubei.js + bgutils-js (PoT) + Supabase Storage upload. Disponibile
    come fallback se in futuro YouTube chiude `c=ANDROID_VR` o introduce IP
    lock rigido. Per attivarlo basta cambiare `BACKEND_REFRESH_URL` della
    skill a puntare a `/refresh-track-storage`.
- **Da fare**:
    1. Pulizia opzionale: rimuovere logging diagnostico `format snapshot` da
       `resolveStreamForClient` (era utile al debug, ora è rumore).
    2. Pulizia opzionale: rimuovere il bucket `audio-cache` se decidi di
       non usare più la variante storage (oppure lasciarlo vuoto, costa 0).
    3. Per prod: tenere yt-dlp aggiornato (`pip install -U yt-dlp` cron
       settimanale) — se YouTube cambia il format selector di
       `c=ANDROID_VR`, una versione vecchia smette di funzionare.
- **Da fare**:
  1. Monitorare se YouTube chiude anche `android_vr` — è probabile entro pochi
     mesi. In quel caso passare a `bgutil-ytdlp-pot-provider` (plugin che
     genera PO Token automaticamente) oppure migrare a `youtubei.js`.
  2. Strada definitiva: sostituire yt-dlp con `youtubei.js` lato BE
     (stessa lib dell'app), che gestisce visitor-data e sessione client-side
     senza dipendere dai PO Token.

### B. Re-resolution mid-stream (priorità ALTA)
- **Riferimento app**: `Event.PlaybackState === Error` in `playback.service.ts`
  + `setPlay` in `libs/player.ts:1004` → controllano `isTrackLocalOrNotExpired`
  *prima* di chiamare `play()`. Se scaduto, `reloadActive` con `hasReloaded` per
  evitare loop.
- **Stato skill**: `PlaybackFailedHandler` già fa retry con `RETRY_TOKEN_PREFIX`,
  ma:
  - retry una volta sola (poi abbandona);
  - non gestisce il caso "buffering silenzioso" (Alexa non emette
    PlaybackFailed → la skill non interviene mai).
- **Da fare**: nessuna API Alexa intercetta il "buffering silenzioso", quindi
  l'unico fix è eliminare le cause a monte (vedi A).

### C. Preload della prossima traccia (priorità MEDIA, blocca il caso 7)
- **Riferimento app**: `PLAYER.preload(2)` su `Event.PlaybackActiveTrackChanged`
  + `TrackPlayer.replace` per pre-warm.
- **Stato skill**: nessun preload. La skill non sa nemmeno qual è la prossima
  traccia (tabella `playback_queue` non esiste).
- **Da fare**: definire schema coda su Supabase (decisione aperta: estendere
  `current_track` con `next_track_*` precaricato dall'app, oppure tabella
  `playback_queue` separata). Poi gestire `PlaybackNearlyFinished` per
  enqueue automatico (oggi gestisce solo il loop).

### D. Cache locale automatica (NON applicabile)
- **Riferimento app**: `TrackCacheService` scarica mp4 cifrato dopo X secondi
  di ascolto reale.
- **Stato skill**: Alexa non ha filesystem persistente lato device per audio
  arbitrario. Salto.

### E. Task queue con priorità e cancellation (priorità BASSA)
- **Riferimento app**: `taskQueue` con priorità (0/10/20) e
  `cancellationPolicy`.
- **Stato skill**: non strettamente necessario in Lambda (single request →
  single response). Saltabile finché non si introducono job in background.

---

### 9. Riproduzione in loop
- **Stato**: Fatto.
- **Trigger**: utente dice *"Alexa, riproduci in loop"* / *"metti in loop"*.
- **Tipo richiesta**: `IntentRequest` con `AMAZON.LoopOnIntent` per attivare; in seguito Alexa invia `AudioPlayer.PlaybackNearlyFinished` ogni volta che la traccia sta per finire.
- **Componenti**:
  - `MusicPlayIntentHandler` (gestisce `AMAZON.LoopOnIntent`): setta `loop_mode = true`, avvia la riproduzione.
  - `PlaybackNearlyFinishedHandler`: nuovo handler che, se `loop_mode = true`, riaccoda la stessa traccia con `PlayBehavior.REPLACE_ENQUEUED` e `offset = 0`.
  - `CancelAndStopIntentHandler`: disattiva `loop_mode` quando l'utente dice stop, così il prossimo play non parte automaticamente in loop.
- **Schema**: nuova colonna `loop_mode BOOLEAN NOT NULL DEFAULT FALSE` su `current_track`.
- **Flusso**:
  1. Utente: *"Alexa, riproduci in loop"* → `LoopOnIntent` → loop_mode=true + Play.
  2. Track sta per finire → Alexa invia `PlaybackNearlyFinished` → handler riaccoda la stessa traccia con offset=0.
  3. Loop continua finché l'utente non dice stop, che disattiva `loop_mode`.
- **Pre-requisito skill model**: `AMAZON.LoopOnIntent` aggiunto agli intent della skill nella Alexa Developer Console.
- **Limitazione**: se l'URL scade durante un loop molto lungo, il loop si interrompe. Soluzione futura: integrare il refresh URL anche nel `PlaybackNearlyFinishedHandler`.

---

### 10. Cambio traccia dall'app (skip manuale utente)
- **Stato**: Parziale.
- **Trigger**: l'utente, mentre usa l'app `beatly`, fa skip avanti/indietro o
  seleziona un'altra traccia. L'app esegue UPSERT su `current_track` con il
  nuovo `youtube_id` (e `track_title`, `track_artist`, `track_duration`,
  `offset = 0`).
- **Pre-requisito**: caso 6 (sync app → Supabase) implementato lato app.
- **Comportamento attuale**:
  - **Skill ferma** (sessione chiusa, nessun audio in corso): al prossimo
    `MusicPlayIntent` la skill legge `current_track`, vede il nuovo
    `youtube_id`, fa refresh se URL scaduto e riproduce la nuova traccia.
    **Funziona già** grazie al flusso esistente del caso 3.
  - **Skill in playback attivo**: Alexa continua a riprodurre la traccia
    precedente fino alla fine o allo stop. Non si "accorge" del cambio
    perché `AudioPlayer` è una pipeline isolata: la skill Lambda non riceve
    eventi finché Alexa non emette `PlaybackNearlyFinished`/`Stopped`/etc.
    **Non implementato** un meccanismo di sincronizzazione live.
- **Opzioni per la sincronizzazione live (out-of-scope per ora)**:
  1. **Alexa Proactive Events**: il backend invia un evento `AMAZON.MediaContent.Update`
    alla skill, che può cambiare track. Richiede registrazione eventi sulla
    Developer Console + autenticazione SMAPI lato BE.
  2. **Sfruttare `PlaybackNearlyFinished`**: già scatta ~10s prima della fine
    della traccia. L'handler può controllare se `current_track.youtube_id` è
    cambiato rispetto al token corrente e, in tal caso, accodare la nuova
    traccia (salto del finale di quella vecchia, ma è il prezzo del sync).
  3. **Comando vocale di refresh**: aggiungere un intent `RefreshIntent`
    (*"Alexa, sincronizza"*) che forza il re-read di `current_track` e
    `Play REPLACE_ALL` con la nuova traccia. Soluzione semplice se l'utente
    accetta di doverlo dire.
- **Raccomandazione**: tenere come Parziale finché non si decide. Il flusso
  "skill ferma" è già coperto dal caso 3 e copre la maggior parte degli
  scenari d'uso reale (l'utente cambia traccia sull'app *prima* di parlare
  ad Alexa, non durante).

---

### 11. Play / Pause / Stop dall'app riflessi su Alexa
- **Stato**: Parziale.
- **Trigger**: l'utente, mentre usa l'app `beatly`, preme play, pausa o stop
  sulla traccia in riproduzione. L'app aggiorna `current_track`:
  - **Pause/Stop**: UPSERT con `offset` finale + `updated_at` (caso 6,
    eventi mobile `PlaybackProgressUpdated` / `PlaybackState=PAUSED|STOPPED`).
  - **Play da pausa**: nessun update strutturale, l'app continua a fare
    progress UPSERT mentre suona.
- **Pre-requisito**: caso 6 implementato lato app (sync app → Supabase).
- **Comportamento attuale**:
  - **Skill ferma**: al prossimo `MusicPlayIntent` la skill riprende dal
    `current_track.offset` salvato dall'app — il punto giusto. Il clamp
    `track_duration` (caso 5/10) protegge da offset stale. **Funziona
    già**.
  - **Skill in playback attivo**:
    - Se l'app va in pausa, Alexa **non** si ferma. Continua finché l'utente
      non dice "Alexa stop" o la traccia finisce.
    - Se l'app fa stop completo, Alexa idem: continua.
    - Se l'app preme play mentre Alexa già suona → **scenario VIETATO** dal
      caso 12 (single active player): la riproduzione concorrente non è
      ammessa. La mutua esclusione va imposta lato app (vedi caso 12).
  Non implementato un meccanismo di sincronizzazione live perché
  `AudioPlayer` è isolato dalla Lambda.
- **Opzioni per la sincronizzazione live (out-of-scope per ora)**:
  1. **Alexa Proactive Events**: il backend, ricevuto l'UPSERT da app
     (tramite trigger Supabase Realtime o webhook), invia un evento
     `AMAZON.MediaContent.Update` o un custom event ad Alexa, che spegne
     l'audio o lo allinea. Richiede SMAPI + registrazione eventi.
  2. **Comando vocale espliciato**: l'utente dice *"Alexa, allinea"* /
     *"Alexa, sincronizza"*. La skill rilegge `current_track` e applica
     stop o play coerente. Più semplice, costa solo aggiungere un intent.
  3. **Schema field `is_playing`** su `current_track` (BOOLEAN): l'app lo
     scrive a ogni cambio di stato, la skill lo controlla a ogni
     `MusicPlayIntent`/`Resume`/`PlaybackNearlyFinished` per decidere se
     riprendere o fermare. Migliora coerenza ma non risolve il caso "skill
     in playback senza eventi entranti".
- **Raccomandazione**: come per il caso 10, tenere Parziale. La parte già
  coperta (resume dall'offset corretto al prossimo "Alexa play") è di gran
  lunga il caso più frequente. Lo stop live di Alexa quando l'app va in
  pausa è un nice-to-have da affrontare quando si introdurranno i
  Proactive Events.

---

### 12. Mutua esclusione device (single active player)
- **Stato**: Da fare.
- **Regola di sistema**: la riproduzione audio dev'essere attiva su **un solo
  device alla volta**. Vietate le sovrapposizioni: app e Alexa non possono
  suonare insieme, due istanze dell'app su device diversi non possono suonare
  insieme. Quando un device "rivendica" il playback, gli altri devono
  fermarsi (preferibilmente in modo automatico).
- **Schema proposto su `current_track`** (migration da fare):
  - `active_device TEXT` — identificatore canonico del device attivo.
    Esempi: `'alexa:<deviceId>'`, `'app:<installationId>'`. `NULL` se
    nessun device sta suonando.
  - `is_playing BOOLEAN NOT NULL DEFAULT FALSE` — stato corrente
    (playing / paused-stopped). Distingue "device attivo che suona" da
    "device attivo in pausa, può riprendere".
  - `playback_state_changed_at TIMESTAMPTZ` — timestamp dell'ultimo cambio
    di stato. Utile per ordinamento e per rilevare "rivendicazioni"
    successive.
- **Lato app**:
  - Subito prima di `play()`: UPSERT con `active_device = 'app:...'`,
    `is_playing = true`. Subscribe a Supabase Realtime su `current_track`:
    se vede `active_device != self`, chiama `pause()` localmente.
  - Su `pause()` / `stop()`: UPSERT con `is_playing = false` (lascia
    `active_device` per consentire il resume sullo stesso device).
- **Lato skill Alexa**:
  - In `MusicPlayIntentHandler` prima della directive `Play`: scrive
    `active_device = 'alexa:<deviceId>'` (`deviceId` viene da
    `input.getRequestEnvelope().getContext().getSystem().getDevice().getDeviceId()`).
    Questo "kicka" l'app via Realtime se era attiva.
  - In `CancelAndStopIntentHandler` (stop su Alexa): UPSERT con
    `is_playing = false`. Lascia `active_device = 'alexa:...'` per resume.
  - In `PlaybackStoppedHandler`: idem (offset finale + `is_playing = false`).
- **Limitazione lato Alexa (ineliminabile senza Proactive Events)**:
  se l'app rivendica `active_device = 'app:...'` mentre Alexa sta suonando,
  Alexa **non** si fermerà automaticamente — la Lambda non riceve eventi
  finché Alexa stessa non emette qualcosa. La regola è quindi rispettata
  *bilateralmente solo se*:
    - L'app sta in pausa quando Alexa parte (caso normale di handover).
    - O si introducono Proactive Events: il BE, su trigger Realtime
      `active_device != 'alexa:...' AND is_playing = true`, invia
      a Alexa un evento che fa scattare `AudioPlayer.Stop`.
  Per ora la copertura è asimmetrica:
    - **Alexa → app**: ✅ (l'app monitora Realtime e si ferma).
    - **app → Alexa**: ⚠️ richiede Proactive Events oppure stop vocale
      manuale dell'utente.
- **Pre-requisiti per implementare**:
  1. Migration SQL: aggiungere `active_device`, `is_playing`,
     `playback_state_changed_at` a `current_track`.
  2. Aggiornare il caso 6 (sync app → Supabase) per scrivere i nuovi campi.
  3. Aggiungere subscription Realtime in `playback.service.ts` lato app per
     reagire ai cambi di `active_device`.
  4. Modificare la skill Java (handler) per scrivere `active_device` e
     `is_playing` ai cambi di stato.
- **Decisione aperta**: se / quando affrontare i Proactive Events per
  chiudere il gap "app → Alexa". Senza, la regola è imposta per il 99% dei
  casi (handover naturale dopo pausa); resta il caso patologico in cui
  l'utente attiva l'app mentre Alexa è in playback senza prima fermarla.

---

### 13. Trasferimento ascolto app → Alexa (non mandatorio)
- **Stato**: Da fare. **Non mandatorio** — feature di comodità, non blocca
  il flusso principale.
- **Trigger**: l'utente, dall'app `beatly`, preme un pulsante UI tipo
  *"Continua su Alexa"* / *"Trasferisci ad Alexa"*. L'idea è continuare
  l'ascolto della traccia corrente dallo stesso punto sul device Alexa
  più vicino, senza dover dare un comando vocale.
- **Pre-requisiti**: caso 6 (sync stato su Supabase) + caso 12 (mutua
  esclusione device).
- **Due livelli di implementazione**:

  **Livello A — Handover assistito (semplice, fattibile subito)**
  - L'app salva su `current_track` lo stato attuale (`offset`, `is_playing
    = false`, `active_device = NULL`).
  - L'app ferma localmente la riproduzione.
  - L'app mostra un prompt all'utente: *"Di' 'Alexa, apri ebeat'"*
    (eventualmente con audio TTS dell'app stessa).
  - L'utente dice il comando, scatta `MusicPlayIntent` → la skill legge
    `current_track`, riprende esattamente dall'offset corrente.
  - **Costo**: solo UI lato app + un campo `active_device` su Supabase.
    La skill funziona già grazie al caso 3.

  **Livello B — Handover automatico (complesso, richiede Proactive Events)**
  - L'app chiama un endpoint del BE (es. `POST /handover-to-alexa`) con
    `user_id` + `device_target` (opzionale: deviceId Alexa specifico).
  - Il BE, autenticato con SMAPI access token dell'utente, invia un
    Proactive Event ad Alexa che fa partire la skill in playback (oppure,
    se Alexa supporta, una directive cross-skill).
  - Alexa riceve l'evento, scatta `LaunchRequest` o equivalente, la skill
    legge `current_track` e riproduce.
  - **Costo**: registrazione Proactive Events sulla Developer Console +
    auth flow SMAPI lato BE per ottenere user-bound access token (separato
    dall'OAuth account linking corrente). Più infrastruttura, più
    permessi richiesti all'utente, più punti di rottura.
- **Direzione inversa Alexa → app**: simmetrica ma più semplice. Caso
  separato (eventuale 14): l'utente dice *"Alexa, passa all'app"* →
  `MusicPlayIntentHandler` aggiorna `active_device = NULL` + `is_playing
  = false`, manda directive `Stop`. L'app, in subscription Realtime, vede
  `is_playing = false` e `active_device = NULL` ma con offset valido →
  può proporre il resume con un toast.
- **Raccomandazione**: partire dal Livello A se mai si decide di affrontare
  questo caso. Costa poco, copre il 90% dello scenario d'uso (l'utente
  vuole "spostare" l'ascolto, e dire una frase ad Alexa è accettabile).
  Il Livello B vale la candela solo se diventa una feature di marketing
  ("trasferisci con un tap"), e in quel caso si fa insieme ai Proactive
  Events necessari per il caso 12.

---

### 14. Sync live durante riproduzione Alexa (non mandatorio)
- **Stato**: Workaround. **Non mandatorio** — caso d'uso patologico
  (l'utente smanetta sull'app mentre Alexa sta suonando). Il caso 12
  scoraggia esplicitamente le interazioni concorrenti con la regola
  single active player.
- **Trigger**: l'utente, mentre Alexa sta riproducendo, agisce sull'app
  beatly (seek a un altro punto, cambio traccia con `current_track` UPSERT
  diverso, ecc.). L'app aggiorna Supabase ma Alexa **non se ne accorge**:
  il device AudioPlayer riceve un URL e un offset al momento della
  directive Play e poi è "cieco" agli aggiornamenti DB. La Lambda è
  stateless, niente subscription Realtime.
- **Workaround attuale (`SyncIntent`)**: comando vocale custom che forza
  la skill a ri-leggere `current_track` e ri-emettere `Play REPLACE_ALL`
  con i nuovi `url` e `offset`.
  - **Trigger vocale**: *"Alexa, chiedi a ebeat di aggiornare"* /
    *"Alexa, aggiorna"* / *"Alexa, sincronizza"*.
  - **Componente**: `MusicPlayIntentHandler` con `name == "SyncIntent"`,
    delega a `PlaybackStarter.start(input, Mode.SYNC)`.
  - **Speech**: *"Aggiorno."* (breve, non invasivo).
  - **Pre-requisito skill model**: nuovo intent custom `SyncIntent`
    aggiunto sulla Developer Console con sample utterances *"aggiorna"*,
    *"sincronizza"*, *"ricarica"*, *"allinea"*, *"rinfresca"*.
- **Soluzione live (Proactive Events, out-of-scope)**: stesso pattern del
  caso 12. Il BE persistente, sottoscritto a Supabase Realtime, vede
  l'UPDATE di `current_track.offset` e invia un Proactive Event ad Alexa
  che ri-emette `Play REPLACE_ALL` automaticamente. Latenza minima ~5-30s.
- **Raccomandazione**: tenere il workaround vocale finché serve. È
  sufficiente per il flusso "ho cambiato qualcosa sull'app, adesso voglio
  che Alexa si allinei". L'utente accetta di doverlo dire.

---

### 15. Ricerca traccia via comando vocale
- **Stato**: Fatto.
- **Trigger**: *"Alexa, chiedi a ebeat di suonare {song} di {artist}"*
  (artista opzionale: *"…di suonare imagine"* funziona uguale).
- **Tipo richiesta**: `IntentRequest` con `PlayTrackIntent` (custom)
  per il turn 1, `AMAZON.YesIntent` / `AMAZON.NoIntent` per il turn 2.
- **Componenti**:
  - `PlayTrackIntentHandler` (turn 1): estrae gli slot, chiama
    `DeezerService.firstMatch(query)`, salva il candidato in
    `sessionAttributes.pendingSearch`, risponde *"Ho trovato {title} di
    {artist}, va bene?"* con `withShouldEndSession(false)`.
  - `YesIntentHandler` (turn 2, guard `pendingSearch != null`):
    `YoutubeResolveService.resolveYoutubeId(title, artist, duration)` →
    `CurrentTrackService.promoteFromSearch(email, deezerTrack, youtubeId)` →
    `RefreshService.refresh()` → `AudioPlayer.Play REPLACE_ALL`.
  - `NoIntentHandler` (guard `pendingSearch != null`): pulisce sessione +
    *"Ok, dimmi un altro brano."*.
  - `DeezerService`: GET `https://api.deezer.com/search/track?q=...&limit=5`,
    API pubblica, niente auth.
  - `YoutubeResolveService`: POST `BACKEND_RESOLVE_URL` con
    `{title, artist, duration}` → ritorna `{youtube_id, candidates}`.
- **BE Node (`be.js`)** — nuovo endpoint `POST /resolve-youtube-id`:
  usa `youtubei.js` (già presente, no PO Token per search anonima),
  prende top 10 risultati di `yt.music.search(q, {type:'song'})`,
  scoring `|durationDiff| - (artistMatch ? 100 : 0)`, ritorna il
  migliore.
- **Schema**: nessuna modifica a `current_track`. I metadati Deezer
  vanno in `track_title`/`track_artist`/`track_duration`, il
  `youtube_id` viene risolto al turn 2. URL stream è popolato da
  `/refresh-track` come sempre.
- **Env var skill**: `BACKEND_RESOLVE_URL` (es.
  `https://<host>/resolve-youtube-id`).
- **Pre-requisito skill model**: intent custom `PlayTrackIntent` con
  slot `song: AMAZON.MusicRecording` e `artist: AMAZON.Musician`,
  più built-in `AMAZON.YesIntent` / `AMAZON.NoIntent` (già nel modello
  in `models/it.json`).
- **Razionale split-turn**:
  - Niente match silenzioso "sbagliato" (lyric video, karaoke, cover):
    l'utente conferma prima del play.
  - Ogni turn fa poco lavoro: Deezer è veloce (~300-500 ms), turn 2
    fa search YouTube + refresh ma può prendersi i suoi 4-6 s senza
    sforare gli 8 s del timeout Alexa.
  - Metadati canonici (Deezer) sopravvivono su `current_track`:
    speech *"Riproduco Imagine di John Lennon."* invece di *"Imagine
    (Official Video Remastered)"*.
- **Limitazione**: se l'utente non risponde "sì/no" entro ~8 s la
  sessione muore e bisogna ricominciare. Niente "second choice" su
  "no": uscita pulita. Estendibile in futuro per proporre il 2°/3°
  candidato.
