#!/bin/sh
# Runs once, on the first start of an empty volume. Creates the least-privilege role the application connects with.
# The schema owner (POSTGRES_USER) is only used by Flyway.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v app_user="$DB_APP_USER" -v app_password="$DB_APP_PASSWORD" <<'EOSQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password';
GRANT CONNECT ON DATABASE :"DBNAME" TO :"app_user";
EOSQL
