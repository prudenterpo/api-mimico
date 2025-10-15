ALTER TABLE match_state
    ADD COLUMN IF NOT EXISTS id UUID DEFAULT gen_random_uuid();

ALTER TABLE match_state
    ADD COLUMN IF NOT EXISTS current_mime_player_id UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE match_state
    ADD COLUMN IF NOT EXISTS is_paused BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE match_state
    DROP CONSTRAINT IF EXISTS match_state_pkey,
    ADD CONSTRAINT match_state_pkey PRIMARY KEY (id);
