-- Surface the latest api-key access date directly on rearm.api_keys so the
-- per-org list query in ApiKeyService.listApiKeyDtoByOrgWithLastAccessDate
-- doesn't have to scan rearm.api_key_access.
--
-- api_key_access logs every authenticated call and grows monotonically; the
-- only consumer of "latest access per key" used to query it via
-- DISTINCT ON, which scaled with total table size (tens of millions on
-- active deployments) rather than with the org's key count. Denormalising
-- the latest date onto api_keys keeps the audit table available for
-- forensic/audit reads while making the dashboard query a plain
-- WHERE org = ? select.
ALTER TABLE rearm.api_keys ADD COLUMN last_access_date timestamptz NULL;

-- Backfill from existing audit rows. NULL stays for keys that have never
-- been used; the read path treats null the same as "no recorded access yet".
UPDATE rearm.api_keys ak
SET last_access_date = la.max_date
FROM (
    SELECT api_key_uuid, MAX(access_date) AS max_date
    FROM rearm.api_key_access
    GROUP BY api_key_uuid
) la
WHERE ak.uuid = la.api_key_uuid;
