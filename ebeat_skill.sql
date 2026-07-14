-- =====================================================================
-- Schema canonico Supabase per la skill ebeat.
-- Eseguibile in modo idempotente (IF NOT EXISTS / DO $$ guard).
-- AGGIORNARE QUESTO FILE OGNI VOLTA che si modifica lo schema DB.
-- =====================================================================
--
-- ⚠️  DEPRECATO dal cutover a Redis (Fase 6 — vedi docs/ALEXA_REDIS_MIGRATION.md).
--     Lo stato Alexa (current_track / playback_queue / alexa_device) è ora
--     gestito su REDIS dal BE (RedisAlexaStore). Queste tabelle, le RLS e la
--     publication Realtime NON sono più lette/scritte dal codice:
--       - la skill scrive/legge via /v1/internal/alexa/* (BE → Redis);
--       - l'app riceve i realtime via SSE /v2/alexa/events (non più Realtime);
--       - /v2/alexa/realtime-token è stato rimosso.
--     Conservate solo per riferimento/rollback. Rimozione (DROP) = azione ops
--     manuale e irreversibile, non inclusa qui. Non estendere queste tabelle.
--
--     NB: la deprecazione riguarda SOLO le tabelle di stato playback qui sopra.
--     La sezione OAuth in fondo (alexa_access_tokens) è ATTIVA e su Postgres.
-- =====================================================================

-- Rinomina tabella urls -> current_track (solo se urls esiste ancora)
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'urls') THEN
    ALTER TABLE urls RENAME TO current_track;
  END IF;
END $$;

-- Nuove colonne (IF NOT EXISTS evita errori su esecuzioni successive)
ALTER TABLE current_track ADD COLUMN IF NOT EXISTS updated_at     TIMESTAMPTZ;
ALTER TABLE current_track ADD COLUMN IF NOT EXISTS url_expires_at TIMESTAMPTZ;
ALTER TABLE current_track ADD COLUMN IF NOT EXISTS track_id       BIGINT;
ALTER TABLE current_track ADD COLUMN IF NOT EXISTS track_title    TEXT;
ALTER TABLE current_track ADD COLUMN IF NOT EXISTS track_artist   TEXT;
ALTER TABLE current_track ADD COLUMN IF NOT EXISTS youtube_id     TEXT;
ALTER TABLE current_track ADD COLUMN IF NOT EXISTS loop_mode      BOOLEAN NOT NULL DEFAULT FALSE;

-- track_duration: durata in secondi, usata per clamp dell'offset
-- (impedisce seek oltre la fine del file → MEDIA_ERROR_SERVICE_UNAVAILABLE).
ALTER TABLE current_track ADD COLUMN IF NOT EXISTS track_duration BIGINT;
COMMENT ON COLUMN current_track.track_duration IS
  'Durata traccia in secondi, usata per clamp dell offset lato skill.';

-- Mutex device + stato playback (casi 11 / 12 / 13).
ALTER TABLE current_track ADD COLUMN IF NOT EXISTS active_device             TEXT;
ALTER TABLE current_track ADD COLUMN IF NOT EXISTS is_playing                BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE current_track ADD COLUMN IF NOT EXISTS playback_state_changed_at TIMESTAMPTZ;

-- Seed Deezer track id per auto-refill radio (caso 15).
-- Pinned per tutta la sessione di playback: ogni refill di playback_queue
-- usa questo seed come base per Deezer Radio, senza drift.
ALTER TABLE current_track ADD COLUMN IF NOT EXISTS radio_seed_track_id BIGINT;
COMMENT ON COLUMN current_track.radio_seed_track_id IS
  'Deezer track id usato come seed per Deezer Radio. Pinned per tutta la sessione di playback radio (caso 15 — auto-refill).';
COMMENT ON COLUMN current_track.active_device IS
  'Identificatore device attivo: alexa:<deviceId>, app:<installationId>, NULL se nessuno (caso 12).';
COMMENT ON COLUMN current_track.is_playing IS
  'true=playing, false=paused/stopped (casi 11/12).';
COMMENT ON COLUMN current_track.playback_state_changed_at IS
  'Timestamp ultimo cambio di active_device o is_playing.';

COMMENT ON TABLE current_track IS
  'DEPRECATO (cutover Redis, Fase 6): stato ora su Redis alexa:ct:<email>. Non letta/scritta dal codice. Solo riferimento/rollback.';

-- Constraint univoco su user_id (necessario per upsert merge-duplicates)
DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'current_track_user_id_unique'
  ) THEN
    ALTER TABLE current_track ADD CONSTRAINT current_track_user_id_unique UNIQUE (user_id);
  END IF;
END $$;

-- RLS su current_track: le scritture passano da service_role (skill + BE, che
-- bypassa RLS). Per il Realtime serve una policy di SELECT: l'app si subscriba a
-- current_track per il refill coda sul next Alexa (caso 7/14). Policy USER-SCOPED:
-- il client Supabase dell'app usa un JWT `authenticated` coniato dal BE
-- (/v2/alexa/realtime-token) con claim `email`, e legge SOLO la propria riga.
-- (Niente read anon ampia: ogni utente vede solo il proprio current_track.)
ALTER TABLE current_track ENABLE ROW LEVEL SECURITY;
DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'current_track' AND policyname = 'current_track_read'
  ) THEN
    CREATE POLICY current_track_read ON current_track FOR SELECT TO authenticated
      USING (user_id = auth.jwt() ->> 'email');
  END IF;
END $$;

-- =====================================================================
-- Coda tracce successive precaricate dall'app (caso 7).
-- L'app riempie con N=3-5 righe; la skill, sullo skip o NearlyFinished,
-- promuove la riga position=1 in current_track e cancella.
-- =====================================================================

CREATE TABLE IF NOT EXISTS playback_queue (
  user_id        TEXT        NOT NULL,
  position       INTEGER     NOT NULL,
  youtube_id     TEXT,
  url            TEXT,
  url_expires_at TIMESTAMPTZ,
  track_id       BIGINT,
  track_title    TEXT,
  track_artist   TEXT,
  track_duration BIGINT,
  added_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_id, position)
);

CREATE INDEX IF NOT EXISTS playback_queue_user_idx ON playback_queue(user_id);

COMMENT ON TABLE playback_queue IS
  'DEPRECATO (cutover Redis, Fase 6): coda ora su Redis ZSET alexa:pq:<email>. Non letta/scritta dal codice. Solo riferimento/rollback.';

ALTER TABLE playback_queue ENABLE ROW LEVEL SECURITY;

-- =====================================================================
-- Registry dei device Alexa per utente.
-- Popolata dalla skill ad ogni invocazione: upsert su (user_id, device_id)
-- con device_id = System.device.deviceId. L'app la legge (via endpoint
-- backend) per mostrare il menù "dispositivi" e scegliere il target.
-- Il `name` è assegnato dall'utente lato app (NULL = nome di default in UI).
-- device_id corrisponde a <id> in current_track.active_device = 'alexa:<deviceId>'.
-- =====================================================================

CREATE TABLE IF NOT EXISTS alexa_device (
  user_id       TEXT        NOT NULL,
  device_id     TEXT        NOT NULL,
  name          TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_id, device_id)
);

CREATE INDEX IF NOT EXISTS alexa_device_user_idx ON alexa_device(user_id);

COMMENT ON TABLE alexa_device IS
  'DEPRECATO (cutover Redis, Fase 6): registry ora su Redis HASH alexa:dev:<email>. Non letta/scritta dal codice. Solo riferimento/rollback.';

ALTER TABLE alexa_device ENABLE ROW LEVEL SECURITY;

-- =====================================================================
-- Supabase Realtime: l'app si subscriba a current_track per detect
-- mutex device (caso 12) e a playback_queue per sync coda (caso 7).
-- =====================================================================

DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
     WHERE pubname = 'supabase_realtime' AND tablename = 'current_track'
  ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE current_track;
  END IF;
END $$;

DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
     WHERE pubname = 'supabase_realtime' AND tablename = 'playback_queue'
  ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE playback_queue;
  END IF;
END $$;

-- =====================================================================
-- OAuth account-linking (ATTIVO — NON deprecato: resta su Postgres).
-- Le tabelle alexa_auth_codes / alexa_refresh_tokens sono gestite altrove;
-- qui aggiungiamo alexa_access_tokens (fix sicurezza: l'access token è un
-- token opaco casuale a scadenza, non più lo UUID utente in chiaro).
-- =====================================================================

CREATE TABLE IF NOT EXISTS alexa_access_tokens (
  token       TEXT        PRIMARY KEY,
  user_id     TEXT        NOT NULL,
  expires_at  TIMESTAMPTZ NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS alexa_access_tokens_user_idx ON alexa_access_tokens(user_id);
CREATE INDEX IF NOT EXISTS alexa_access_tokens_expires_idx ON alexa_access_tokens(expires_at);

COMMENT ON TABLE alexa_access_tokens IS
  'Access token OAuth opachi a scadenza (ATTIVA). token -> user_id, validato dal BE (POST /internal/alexa/resolve). Sostituisce lo UUID utente in chiaro come bearer.';
COMMENT ON COLUMN alexa_access_tokens.token IS
  'SHA-256 (hex) del token, NON il grezzo: un leak del DB non è riutilizzabile. Idem per alexa_auth_codes.code e alexa_refresh_tokens.token.';

ALTER TABLE alexa_access_tokens ENABLE ROW LEVEL SECURITY;
