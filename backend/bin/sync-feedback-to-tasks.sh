#!/usr/bin/env bash
# Sync unprocessed bug reports from audit_log to TaskTracker.
# Usage: ./backend/bin/sync-feedback-to-tasks.sh
#
# Requires: psql, jq, and a running TaskTracker server.
# DB credentials read from environment or defaults.

set -euo pipefail

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-docforge}"
PGUSER="${PGUSER:-docforge}"
PGPASSWORD="${PGPASSWORD:-docforge}"

TASKTRACKER_URL="${TASKTRACKER_URL:-http://localhost:8765}"

echo "=== Bug Report → TaskTracker sync ==="
echo "DB: $PGHOST:$PGPORT/$PGDATABASE"

REPORTS=$(PGPASSWORD="$PGPASSWORD" psql -h "$PGHOST" -p "$PGPORT" -d "$PGDATABASE" -U "$PGUSER" -t -A -F $'\t' \
  -c "SELECT id, created_at, details::text FROM audit_log WHERE entity_type = 'FEEDBACK' AND action = 'BUG_REPORT' AND (details::jsonb ->> 'task_id') IS NULL ORDER BY created_at;" 2>/dev/null)

if [ -z "$REPORTS" ]; then
  echo "No new reports to sync."
  exit 0
fi

COUNT=0
while IFS=$'\t' read -r ID CREATED_AT DETAILS; do
  DESCRIPTION=$(echo "$DETAILS" | jq -r '.description // "—"')
  ELEMENT=$(echo "$DETAILS" | jq -r '.element // "—"')
  URL=$(echo "$DETAILS" | jq -r '.url // "—"')

  TITLE="${DESCRIPTION:0:80}"
  BODY="**Источник:** feedback form
**Дата:** $CREATED_AT
**URL:** $URL
**Элемент:** \`$ELEMENT\`
**Описание:** $DESCRIPTION"

  echo "---"
  echo "Creating task: $TITLE"

  RESPONSE=$(curl -s -X POST "$TASKTRACKER_URL/api/v1/tasks" \
    -H "Content-Type: application/json" \
    -d "$(jq -n \
      --arg title "$TITLE" \
      --arg body "$BODY" \
      '{type: "bug", title: $title, description: $body, tags: ["feedback", "bug-report"]}')")

  TASK_ID=$(echo "$RESPONSE" | jq -r '.id // empty')

  if [ -n "$TASK_ID" ]; then
    PGPASSWORD="$PGPASSWORD" psql -h "$PGHOST" -p "$PGPORT" -d "$PGDATABASE" -U "$PGUSER" -c \
      "UPDATE audit_log SET details = jsonb_set(details::jsonb, '{task_id}', to_jsonb('$TASK_ID'::text)) WHERE id = '$ID';" >/dev/null 2>&1
    echo "  → Created $TASK_ID, marked as processed"
    COUNT=$((COUNT + 1))
  else
    echo "  → Failed to create task: $(echo "$RESPONSE" | jq -r '.message // "unknown error"')"
  fi
done <<< "$REPORTS"

echo "=== Done: $COUNT task(s) created ==="
