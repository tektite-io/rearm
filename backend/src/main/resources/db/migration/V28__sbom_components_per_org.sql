-- Pin sbom_components and release_sbom_components to an org.
--
-- Until now sbom_components was globally unique by canonical_purl, which
-- meant the same row was shared across orgs and impact-analysis queries
-- could leak cross-org releases (the global join surfaced every org's
-- release_sbom_components rows for a given canonical, then the per-row
-- org check lived only at the release-load step and turned every cross-
-- org hit into a noisy WARN).
--
-- New model:
--   - sbom_components carries org; uniqueness is now (org, canonical_purl)
--     so the same canonical purl can exist as one row per org (cleanest
--     tenant isolation — no shared global state for component identity).
--   - release_sbom_components carries org; every write derives it from
--     the release row and the service validates the match.
--
-- Per the cutover plan we drop existing rows rather than backfill — the
-- sbom-reconcile queue will rebuild from BOM artifacts on the next tick
-- (or sooner via the operator force-reconcile mutation). The releases
-- queue itself is re-seeded by the same trailing UPDATE that V25 used
-- for the initial backfill.

TRUNCATE TABLE rearm.release_sbom_components;
TRUNCATE TABLE rearm.sbom_components CASCADE;

ALTER TABLE rearm.sbom_components
    ADD COLUMN org uuid NOT NULL;

ALTER TABLE rearm.sbom_components
    DROP CONSTRAINT IF EXISTS sbom_components_canonical_purl_unique;

ALTER TABLE rearm.sbom_components
    ADD CONSTRAINT sbom_components_org_canonical_purl_unique UNIQUE (org, canonical_purl);

CREATE INDEX sbom_components_org_idx
    ON rearm.sbom_components (org);

ALTER TABLE rearm.release_sbom_components
    ADD COLUMN org uuid NOT NULL;

CREATE INDEX release_sbom_components_org_idx
    ON rearm.release_sbom_components (org);

-- Re-enqueue every non-archived release so the scheduler / operator
-- force-reconcile rebuilds the now-empty tables. Mirrors V25's tail
-- and V27's safety re-backfill.
UPDATE rearm.releases
SET flow_control = jsonb_set(
        coalesce(flow_control, '{}'::jsonb),
        '{sbomReconcileRequestedAt}',
        to_jsonb(now()),
        true)
WHERE coalesce(record_data->>'status', 'ACTIVE') != 'ARCHIVED';
