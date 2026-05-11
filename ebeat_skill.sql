-- =====================================================================
-- Schema canonico Supabase per la skill ebeat.
-- Eseguibile in modo idempotente (IF NOT EXISTS / DO $$ guard).
-- AGGIORNARE QUESTO FILE OGNI VOLTA che si modifica lo schema DB.
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

-- Constraint univoco su user_id (necessario per upsert merge-duplicates)
DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'current_track_user_id_unique'
  ) THEN
    ALTER TABLE current_track ADD CONSTRAINT current_track_user_id_unique UNIQUE (user_id);
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
  'Coda tracce successive precaricate dall app (caso 7). position=1 e la prossima dopo current_track.';

ALTER TABLE playback_queue ENABLE ROW LEVEL SECURITY;

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
