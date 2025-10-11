DROP TABLE IF EXISTS round CASCADE;
DROP TABLE IF EXISTS team_player CASCADE;
DROP TABLE IF EXISTS match_team CASCADE;
DROP TABLE IF EXISTS team CASCADE;
DROP TABLE IF EXISTS match CASCADE;
DROP TABLE IF EXISTS board_space CASCADE;
DROP TABLE IF EXISTS word CASCADE;
DROP TABLE IF EXISTS word_category CASCADE;
DROP TABLE IF EXISTS game_table CASCADE;

CREATE TABLE game_tables (
                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             name VARCHAR(100) NOT NULL,
                             host_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                             status VARCHAR(20) NOT NULL CHECK (status IN ('waiting', 'in_progress', 'finished')),
                             created_at TIMESTAMP NOT NULL DEFAULT now(),
                             updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE matches (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         table_id UUID NOT NULL REFERENCES game_tables(id) ON DELETE CASCADE,
                         winner_team CHAR(1) CHECK (winner_team IN ('A', 'B')),
                         started_at TIMESTAMP NOT NULL DEFAULT now(),
                         finished_at TIMESTAMP,
                         CONSTRAINT chk_match_time CHECK (finished_at IS NULL OR finished_at >= started_at)
);

CREATE TABLE match_players (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               match_id UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
                               user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               team CHAR(1) NOT NULL CHECK (team IN ('A', 'B')),
                               player_order SMALLINT NOT NULL CHECK (player_order BETWEEN 1 AND 4),
                               CONSTRAINT unique_match_user UNIQUE(match_id, user_id),
                               CONSTRAINT unique_match_team_order UNIQUE(match_id, team, player_order)
);

CREATE TABLE word_categories (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 name VARCHAR(50) NOT NULL UNIQUE,
                                 display_name VARCHAR(100) NOT NULL
);

CREATE TABLE words (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       category_id UUID NOT NULL REFERENCES word_categories(id) ON DELETE CASCADE,
                       text VARCHAR(100) NOT NULL,
                       difficulty SMALLINT NOT NULL CHECK (difficulty BETWEEN 1 AND 3),
                       CONSTRAINT unique_word_per_category UNIQUE(text, category_id)
);

CREATE TABLE match_state (
                             match_id UUID PRIMARY KEY REFERENCES matches(id) ON DELETE CASCADE,
                             current_team CHAR(1) NOT NULL CHECK (current_team IN ('A', 'B')),
                             current_player_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                             team_a_position SMALLINT NOT NULL DEFAULT 0 CHECK (team_a_position BETWEEN 0 AND 52),
                             team_b_position SMALLINT NOT NULL DEFAULT 0 CHECK (team_b_position BETWEEN 0 AND 52),
                             current_word_id UUID REFERENCES words(id) ON DELETE SET NULL,
                             round_started_at TIMESTAMP,
                             round_expires_at TIMESTAMP,
                             last_activity_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_game_tables_status ON game_tables(status);
CREATE INDEX idx_game_tables_host_user_id ON game_tables(host_user_id);

CREATE INDEX idx_matches_table_id ON matches(table_id);
CREATE INDEX idx_active_matches ON matches(started_at) WHERE finished_at IS NULL;

CREATE INDEX idx_match_players_match_id ON match_players(match_id);
CREATE INDEX idx_match_players_user_id ON match_players(user_id);
CREATE INDEX idx_match_players_team ON match_players(match_id, team);

CREATE INDEX idx_word_categories_name ON word_categories(name);

CREATE INDEX idx_words_category_id ON words(category_id);
CREATE INDEX idx_words_category_difficulty ON words(category_id, difficulty);

CREATE INDEX idx_match_state_expires_at ON match_state(round_expires_at) WHERE round_expires_at IS NOT NULL;
CREATE INDEX idx_match_state_last_activity ON match_state(last_activity_at);

INSERT INTO word_categories (name, display_name) VALUES
                                                     ('eu_sou', 'Eu sou'),
                                                     ('eu_faco', 'Eu faço'),
                                                     ('objeto', 'Objeto')
ON CONFLICT (name) DO NOTHING;