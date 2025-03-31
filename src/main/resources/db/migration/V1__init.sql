CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE "user" (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nickname TEXT NOT NULL UNIQUE,
    avatar_url TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP
);

CREATE TABLE auth_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES "user"(id) ON DELETE CASCADE,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    last_session_id TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP
);

CREATE TABLE game_table (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_id UUID NOT NULL REFERENCES "user"(id),
    player_count INTEGER NOT NULL CHECK (player_count IN (4, 6)),
    status TEXT NOT NULL,
    current_category TEXT,
    current_position INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE team (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_table_id UUID NOT NULL REFERENCES game_table(id) ON DELETE CASCADE,
    name TEXT NOT NULL
);

CREATE TABLE team_player (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES team(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES "user"(id),
    UNIQUE(team_id, user_id)
);

CREATE TABLE match (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_table_id UUID NOT NULL REFERENCES game_table(id) ON DELETE CASCADE,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP
);

CREATE TABLE match_team (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id UUID NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    team_id UUID NOT NULL REFERENCES team(id) ON DELETE CASCADE,
    position INTEGER NOT NULL DEFAULT 0,
    UNIQUE(match_id, team_id)
);

CREATE TABLE word_category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE word (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES word_category(id) ON DELETE CASCADE,
    text TEXT NOT NULL,
    difficulty INT NOT NULL CHECK (difficulty BETWEEN 1 AND 3),
    type TEXT,
    status TEXT,
    UNIQUE (text, category_id)
);


CREATE TABLE board_space (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    position INTEGER NOT NULL UNIQUE,
    effect TEXT NOT NULL
);

CREATE TABLE round (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id UUID NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    player_id UUID NOT NULL REFERENCES "user"(id),
    team_id UUID NOT NULL REFERENCES team(id) ON DELETE CASCADE,
    category TEXT NOT NULL,
    word_id UUID NOT NULL REFERENCES word(id) ON DELETE CASCADE,
    success BOOLEAN NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NOT NULL
);
