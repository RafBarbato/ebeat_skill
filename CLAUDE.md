# ebeat skill — appunti di progetto

## Sincronizzazione traccia corrente: beatly → Supabase

### Obiettivo
Scrivere in tempo reale lo stato di riproduzione su Supabase (tabella `current_track`)
in modo che la skill Alexa possa riprendere l'ascolto dal punto esatto in cui l'utente
si è fermato sull'app mobile.

### Tabella Supabase: `current_track`

| Campo           | Tipo        | Descrizione                                  |
|-----------------|-------------|----------------------------------------------|
| `user_id`       | TEXT        | Email dell'utente (chiave di ricerca)        |
| `url`           | TEXT        | URL stream YouTube                           |
| `url_expires_at`| TIMESTAMPTZ | Scadenza URL (da `expireAt` della cache)     |
| `offset`        | BIGINT      | Posizione in millisecondi                    |
| `track_id`      | BIGINT      | ID Deezer della traccia                      |
| `track_title`   | TEXT        | Titolo                                       |
| `track_artist`  | TEXT        | Artista                                      |
| `updated_at`    | TIMESTAMPTZ | Ultimo aggiornamento                         |

La riga è unica per utente (UPSERT su `user_id`).

### Dove intervenire in beatly

#### 1. Cambio traccia — scrivere url + metadati
**File:** `app/src/services/playback.service.ts`
**Evento:** `Event.PlaybackActiveTrackChanged`

Quando parte una nuova traccia, eseguire UPSERT con tutti i campi:
- `url` e `url_expires_at` dalla cache della traccia (campo `expireAt`)
- `track_id`, `track_title`, `track_artist` dall'oggetto track
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
| 1  | Avvio skill da comando vocale                       | Fatto            |
| 2  | Stop della skill                                    | Fatto            |
| 3  | Avvio ultima traccia attiva al momento              | Fatto            |
| 4  | Avvio ultima traccia attiva dopo scadenza URL       | Fatto            |
| 5  | Stop della musica                                   | Fatto            |
| 6  | Aggiornamento traccia attiva su Supabase            | Da fare          |
| 7  | Passaggio a traccia successiva (playlist)           | Da fare          |

---

### 1. Avvio skill da comando vocale
- **Stato**: Fatto.
- **Trigger**: utente dice *"Alexa, apri ebeat"*
- **Tipo richiesta**: `LaunchRequest`
- **Componente**: `LaunchHandler`
- **Flusso**: nessuna chiamata esterna, solo benvenuto vocale.
- **Risposta**: messaggio che invita l'utente a dire *"play"* per avviare la riproduzione.
- **Stato sessione**: aperta, con reprompt.

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

### 5. Stop della musica
- **Stato**: Fatto.
- **Trigger**: utente dice *"Alexa, stop"* / *"pausa"* durante la riproduzione audio (skill in stato `AudioPlayer`, sessione già chiusa).
- **Tipo richiesta**: `IntentRequest` con `AMAZON.StopIntent` / `AMAZON.PauseIntent` / `AMAZON.CancelIntent`, seguito da `AudioPlayer.PlaybackStopped`.
- **Componenti**: `CancelAndStopIntentHandler` (ferma lo stream) + `PlaybackStoppedHandler` (persiste l'offset).
- **Flusso**:
  1. `CancelAndStopIntentHandler` emette `AudioPlayer.Stop` directive per fermare lo stream e chiudere la skill.
  2. Alexa invia automaticamente `AudioPlayer.PlaybackStopped` con `offsetInMilliseconds` corrente.
  3. `PlaybackStoppedHandler` risolve l'utente via `AccountService` e fa PATCH su `current_track.offset` tramite `CurrentTrackService.updateOffset()`.
- **Risultato**: alla successiva invocazione del caso 3, la riproduzione riprende esattamente dallo stesso punto.

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
- **Stato**: Da fare.
- **Trigger vocale**: utente dice *"Alexa, prossima"* / *"avanti"* durante la riproduzione.
- **Trigger automatico**: fine della traccia corrente (`AudioPlayer.PlaybackNearlyFinished` / `PlaybackFinished`).
- **Tipo richiesta**: `IntentRequest` con `AMAZON.NextIntent` **oppure** `AudioPlayer.PlaybackNearlyFinished`.
- **Componente**: `NextIntentHandler` / `PlaybackNearlyFinishedHandler` (**da implementare**).
- **Pre-requisiti su Supabase (schema da definire)**:
  - Tabella `playback_queue` (o estensione di `current_track`) con la coda corrente: array di tracce ordinate con `url`, `url_expires_at`, `offset = 0`, metadati.
  - Oppure: campo `next_track_*` su `current_track` con la sola traccia successiva precaricata dall'app.
- **Flusso ipotizzato**:
  1. Dal `user_id` recuperare la playlist o la prossima traccia.
  2. Aggiornare `current_track` con i dati della nuova traccia.
  3. Emettere `AudioPlayer.Play` con `PlayBehavior.REPLACE_ALL` (intent vocale) o `REPLACE_ENQUEUED` (transizione automatica).
- **Decisione aperta**: chi pre-carica gli URL della playlist? L'app mobile (semplice ma offline non funziona) o un servizio backend (più robusto ma più infrastruttura).
