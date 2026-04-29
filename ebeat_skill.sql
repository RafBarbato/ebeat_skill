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

-- Constraint univoco su user_id (necessario per upsert merge-duplicates)
DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'current_track_user_id_unique'
  ) THEN
    ALTER TABLE current_track ADD CONSTRAINT current_track_user_id_unique UNIQUE (user_id);
  END IF;
END $$;
