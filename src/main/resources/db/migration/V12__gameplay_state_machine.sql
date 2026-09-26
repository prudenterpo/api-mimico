ALTER TABLE matches ADD COLUMN IF NOT EXISTS match_status VARCHAR(32) NOT NULL DEFAULT 'MATCH_SETUP';
ALTER TABLE matches ADD COLUMN IF NOT EXISTS finish_reason VARCHAR(32);
ALTER TABLE matches ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE matches DROP CONSTRAINT IF EXISTS chk_matches_status;
ALTER TABLE matches ADD CONSTRAINT chk_matches_status
    CHECK (match_status IN ('MATCH_SETUP', 'MATCH_ACTIVE', 'MATCH_PAUSED', 'MATCH_FINISHED'));

ALTER TABLE matches DROP CONSTRAINT IF EXISTS chk_matches_finish_reason;
ALTER TABLE matches ADD CONSTRAINT chk_matches_finish_reason
    CHECK (finish_reason IS NULL OR finish_reason IN ('BOARD_WIN', 'RECONNECTION_FORFEIT', 'MANUAL_FORFEIT', 'ADMIN_CANCELLED'));

ALTER TABLE match_players ADD COLUMN IF NOT EXISTS nickname VARCHAR(255);
ALTER TABLE match_players ADD COLUMN IF NOT EXISTS last_mime_round INTEGER;

ALTER TABLE match_state ADD COLUMN IF NOT EXISTS round_state VARCHAR(40);
ALTER TABLE match_state ADD COLUMN IF NOT EXISTS paused_at TIMESTAMP;
ALTER TABLE match_state ADD COLUMN IF NOT EXISTS pause_reason VARCHAR(40);
ALTER TABLE match_state ADD COLUMN IF NOT EXISTS disconnected_user_id UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE match_state ADD COLUMN IF NOT EXISTS reconnect_deadline TIMESTAMP;
ALTER TABLE match_state ADD COLUMN IF NOT EXISTS remaining_round_seconds_on_pause INTEGER;
ALTER TABLE match_state ADD COLUMN IF NOT EXISTS sorteio_player_a_id UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE match_state ADD COLUMN IF NOT EXISTS sorteio_player_b_id UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE match_state ADD COLUMN IF NOT EXISTS sorteio_roll_a INTEGER;
ALTER TABLE match_state ADD COLUMN IF NOT EXISTS sorteio_roll_b INTEGER;

ALTER TABLE match_state DROP CONSTRAINT IF EXISTS chk_match_state_round_state;
ALTER TABLE match_state ADD CONSTRAINT chk_match_state_round_state
    CHECK (round_state IS NULL OR round_state IN (
        'ROUND_WAITING_FOR_DICE',
        'ROUND_WAITING_FOR_WORD_SELECTION',
        'ROUND_GUESSING',
        'ROUND_RESOLVED'
    ));

ALTER TABLE match_state DROP CONSTRAINT IF EXISTS chk_match_state_pause_reason;
ALTER TABLE match_state ADD CONSTRAINT chk_match_state_pause_reason
    CHECK (pause_reason IS NULL OR pause_reason IN ('PLAYER_DISCONNECTED', 'MIME_MEDIA_FAILED'));

CREATE TABLE IF NOT EXISTS game_rounds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    round_number INTEGER NOT NULL,
    round_state VARCHAR(40) NOT NULL,
    current_team CHAR(1) NOT NULL CHECK (current_team IN ('A', 'B')),
    mime_player_id UUID NOT NULL REFERENCES match_players(id) ON DELETE CASCADE,
    dice_value INTEGER CHECK (dice_value IS NULL OR dice_value BETWEEN 1 AND 6),
    landing_tile INTEGER CHECK (landing_tile IS NULL OR landing_tile BETWEEN 0 AND 52),
    is_special_tile BOOLEAN NOT NULL DEFAULT FALSE,
    selected_word_id UUID REFERENCES words(id) ON DELETE SET NULL,
    started_at TIMESTAMP,
    expires_at TIMESTAMP,
    resolved_at TIMESTAMP,
    resolution VARCHAR(32),
    CONSTRAINT uq_game_rounds_match_number UNIQUE (match_id, round_number),
    CONSTRAINT chk_game_rounds_state CHECK (round_state IN (
        'ROUND_WAITING_FOR_DICE',
        'ROUND_WAITING_FOR_WORD_SELECTION',
        'ROUND_GUESSING',
        'ROUND_RESOLVED'
    )),
    CONSTRAINT chk_game_rounds_resolution CHECK (resolution IS NULL OR resolution IN (
        'CORRECT_GUESS',
        'STEAL',
        'TIMEOUT',
        'MATCH_FINISHED',
        'FORFEIT'
    ))
);

CREATE TABLE IF NOT EXISTS round_word_card (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    round_id UUID NOT NULL REFERENCES game_rounds(id) ON DELETE CASCADE,
    word_id UUID NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    category VARCHAR(20) NOT NULL,
    selection_order INTEGER NOT NULL CHECK (selection_order BETWEEN 1 AND 3),
    CONSTRAINT uq_round_word_card_category UNIQUE (round_id, category),
    CONSTRAINT uq_round_word_card_word UNIQUE (round_id, word_id),
    CONSTRAINT uq_round_word_card_order UNIQUE (round_id, selection_order),
    CONSTRAINT chk_round_word_card_category CHECK (category IN ('EU_SOU', 'EU_FACO', 'OBJETO'))
);

CREATE INDEX IF NOT EXISTS idx_game_rounds_match_id ON game_rounds(match_id);
CREATE INDEX IF NOT EXISTS idx_round_word_card_round_id ON round_word_card(round_id);
CREATE INDEX IF NOT EXISTS idx_match_state_guessing_expires
    ON match_state (round_expires_at)
    WHERE round_state = 'ROUND_GUESSING' AND is_paused = FALSE;
