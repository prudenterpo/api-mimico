ALTER TABLE "user" RENAME TO users;

ALTER TABLE auth_credentials
    DROP CONSTRAINT auth_credentials_user_id_fkey,
    ADD CONSTRAINT auth_credentials_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE game_table
    DROP CONSTRAINT game_table_host_id_fkey,
    ADD CONSTRAINT game_table_host_id_fkey
        FOREIGN KEY (host_id) REFERENCES users(id);

ALTER TABLE team_player
    DROP CONSTRAINT team_player_user_id_fkey,
    ADD CONSTRAINT team_player_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE round
    DROP CONSTRAINT round_player_id_fkey,
    ADD CONSTRAINT round_player_id_fkey
        FOREIGN KEY (player_id) REFERENCES users(id);