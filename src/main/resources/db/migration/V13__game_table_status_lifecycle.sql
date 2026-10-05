ALTER TABLE game_tables DROP CONSTRAINT IF EXISTS game_tables_status_check;

ALTER TABLE game_tables
    ALTER COLUMN status TYPE VARCHAR(32);

UPDATE game_tables
SET status = CASE status
    WHEN 'WAITING' THEN 'TABLE_WAITING'
    WHEN 'waiting' THEN 'TABLE_WAITING'
    WHEN 'IN_PROGRESS' THEN 'TABLE_IN_MATCH'
    WHEN 'in_progress' THEN 'TABLE_IN_MATCH'
    WHEN 'FINISHED' THEN 'TABLE_CLOSED'
    WHEN 'finished' THEN 'TABLE_CLOSED'
    ELSE status
END;

ALTER TABLE game_tables
    ADD CONSTRAINT game_tables_status_check
        CHECK (status IN (
            'TABLE_WAITING',
            'TABLE_READY_TO_START',
            'TABLE_IN_MATCH',
            'TABLE_BETWEEN_MATCHES',
            'TABLE_CLOSED'
        ));
