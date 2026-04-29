# Sincronizzazione traccia corrente - beatly → Supabase

## Obiettivo
Scrivere in tempo reale lo stato di riproduzione su Supabase (tabella `current_track`)
in modo che la skill Alexa possa riprendere l'ascolto dal punto esatto in cui l'utente
si è fermato sull'app mobile.

---

## Tabella Supabase: `current_track`

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

---

## Dove intervenire in beatly

### 1. Cambio traccia — scrivere url + metadati
**File:** `app/src/services/playback.service.ts`
**Evento:** `Event.PlaybackActiveTrackChanged`

Quando parte una nuova traccia, eseguire UPSERT con tutti i campi:
- `url` e `url_expires_at` dalla cache della traccia (campo `expireAt`)
- `track_id`, `track_title`, `track_artist` dall'oggetto track
- `offset = 0`
- `updated_at = now()`

### 2. Aggiornamento offset periodico
**File:** `app/src/services/playback.service.ts`
**Evento:** `Event.PlaybackProgressUpdated` (ogni 5 secondi)

Eseguire UPSERT con solo:
- `offset` (posizione corrente in ms)
- `updated_at = now()`

### 3. Pausa / Stop
**File:** `app/src/services/playback.service.ts`
**Evento:** `Event.PlaybackState` (quando stato diventa PAUSED o STOPPED)

Eseguire UPSERT con:
- `offset` finale
- `updated_at = now()`

---

## Chiamata Supabase (TypeScript)

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

---

## Note
- L'aggiornamento dell'offset ogni 5s genera molte scritture: valutare di ridurre
  la frequenza a 15-30s se il traffico su Supabase diventa eccessivo.
- Gli URL YouTube scadono (tipicamente dopo alcune ore). Se Alexa trova
  `url_expires_at` nel passato, avvisa l'utente di riaprire l'app.
- `position` da `Event.PlaybackProgressUpdated` è in secondi (float),
  la skill Alexa si aspetta l'offset in millisecondi.
