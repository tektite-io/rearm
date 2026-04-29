package io.reliza.dto;

import java.time.ZonedDateTime;
import java.util.UUID;

import io.reliza.model.dto.ReleaseMetricsDto.VulnerabilityDto;

/**
 * A vulnerability that was detected in some prior release on the target's lineage but is
 * absent from the target release's current metrics. Carries the source vulnerability
 * (severity, source, aliases, purl) as it was last observed before the resolving release,
 * plus provenance pointing at the release immediately after which the vulnerability stops
 * appearing on the lineage.
 *
 * @see io.reliza.service.FindingComparisonService#findHistoricallyResolvedForRelease
 */
public record HistoricallyResolvedFinding(
    VulnerabilityDto vulnerability,
    UUID resolvingReleaseUuid,
    String resolvingReleaseVersion,
    ZonedDateTime resolvingReleaseCreatedDate
) {}
