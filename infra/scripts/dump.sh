#!/bin/bash

echo "Starting database backup..."

docker exex -t mimico_postgres pg_dump -U mimico mimico_db > ./db/backup.sql

echo "Database backup has finished in: mimico-infra/db/backup.sql"