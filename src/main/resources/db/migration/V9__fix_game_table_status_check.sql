ALTER TABLE game_tables DROP CONSTRAINT game_tables_status_check;

ALTER TABLE game_tables
    ADD CONSTRAINT game_tables_status_check
        CHECK (status = ANY (ARRAY['WAITING', 'IN_PROGRESS', 'FINISHED']));
