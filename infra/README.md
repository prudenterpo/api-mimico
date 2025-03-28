# Mimico Infra

This repository contains the Docker infrastructure for the Mimico project, specifically the PostgreSQL setup used in development.

## Services

- PostgreSQL 15 (Docker)
- Backup and restore scripts for development database sync

## How to Run

Start the PostgreSQL container:

```bash
docker-compose up -d

## Backup and Restore database

./scripts/dump.sh
./scripts/restore.sh

