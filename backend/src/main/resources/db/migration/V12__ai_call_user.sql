-- Attribute every AI call to the user who triggered it (per-user usage dashboard).
ALTER TABLE ai_call ADD COLUMN user_id BIGINT REFERENCES app_user (id);
CREATE INDEX ai_call_user_idx ON ai_call (user_id, created_at DESC);

-- Backfill from the audit trail: each AI call wrote an audit event carrying its aiCallId and the acting user.
UPDATE ai_call c
SET user_id = a.actor_user_id
FROM audit_event a
WHERE a.action LIKE 'ai.%'
  AND a.actor_user_id IS NOT NULL
  AND a.details ? 'aiCallId'
  AND (a.details ->> 'aiCallId')::bigint = c.id;
