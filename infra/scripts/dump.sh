#!/bin/bash

echo "Starting database backup..."

docker exec -t mimico_postgres pg_dump -U mimico mimico_db > ./db/backup.sql

echo "Database backup has finished in: infra/db/backup.sql"