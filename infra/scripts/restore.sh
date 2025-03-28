#!/bin/bash

echo "Starting database restoring..."

cat ./db/backup.sql | docker exec -i mimico_postgres psql -U mimico -d mimico_db

echo "✅ Database restored successfully!"
