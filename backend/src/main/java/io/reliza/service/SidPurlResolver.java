/**
* Copyright Reliza Incorporated. 2019 - 2026. Licensed under the terms of AGPL-3.0-only.
*/

package io.reliza.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.reliza.common.CommonVariables.PerspectiveType;
import io.reliza.common.CommonVariables.SidPurlMode;
import io.reliza.common.CommonVariables.SidPurlOverride;
import io.reliza.exceptions.RelizaException;
import io.reliza.model.ComponentData;
import io.reliza.model.DeliverableData.BelongsToOrganization;
import io.reliza.model.OrganizationData;
import io.reliza.model.saas.PerspectiveData;
import io.reliza.service.oss.OssPerspectiveService;

/**
 * Resolves the effective sid (Software IDentification) PURL policy for a component
 * at release-creation time. Throws {@link RelizaException} on perspective-level
 * ambiguity. Does not throw on "enabled with invalid segments" — that's the
 * orchestrator's call to make.
 */
@Service
public class SidPurlResolver {

	@Autowired
	private OssPerspectiveService ossPerspectiveService;

	/** Which rule produced the resolved policy. Telemetry/tests only — not load-bearing. */
	public enum SidPurlSource {
		EXTERNAL_COMPONENT,
		ORG_DISABLED,
		ORG_STRICT,
		COMPONENT_OVERRIDE,
		PERSPECTIVE_OVERRIDE,
		ORG_FLEXIBLE_DEFAULT;
	}

	public record ResolvedSidPolicy(boolean enabled, List<String> authoritySegments, SidPurlSource source) {}

	/**
	 * @throws RelizaException if multiple real perspectives carry conflicting overrides
	 *   or conflicting authority segments
	 */
	public ResolvedSidPolicy resolveForComponent(ComponentData cd, OrganizationData org) throws RelizaException {
		// EXTERNAL is a hard ceiling — never stamp our authority on third-party software.
		if (cd.getIsInternalOrDefault() == BelongsToOrganization.EXTERNAL) {
			return new ResolvedSidPolicy(false, null, SidPurlSource.EXTERNAL_COMPONENT);
		}

		OrganizationData.Settings settings = org.getSettings();
		SidPurlMode orgMode = settings != null ? settings.getSidPurlModeOrDefault() : SidPurlMode.DISABLED;
		List<String> orgSegments = settings != null ? settings.getSidAuthoritySegments() : null;

		if (orgMode == SidPurlMode.DISABLED) {
			return new ResolvedSidPolicy(false, null, SidPurlSource.ORG_DISABLED);
		}
		if (orgMode == SidPurlMode.ENABLED_STRICT) {
			return new ResolvedSidPolicy(true, orgSegments, SidPurlSource.ORG_STRICT);
		}
		// ENABLED_FLEXIBLE — layered resolution below.

		// Synthetic product-derived perspectives are skipped — only real perspectives carry overrides.
		List<PerspectiveData> realPerspectives = collectRealPerspectives(cd);

		Boolean enabled = decideEnabled(cd, realPerspectives);
		SidPurlSource source;
		if (cd.getSidPurlOverride() != null && cd.getSidPurlOverride() != SidPurlOverride.INHERIT) {
			source = SidPurlSource.COMPONENT_OVERRIDE;
		} else if (enabled != null) {
			source = SidPurlSource.PERSPECTIVE_OVERRIDE;
		} else {
			return new ResolvedSidPolicy(false, null, SidPurlSource.ORG_FLEXIBLE_DEFAULT);
		}

		if (Boolean.FALSE.equals(enabled)) {
			return new ResolvedSidPolicy(false, null, source);
		}

		List<String> resolvedSegments = decideSegments(cd, realPerspectives, orgSegments);
		return new ResolvedSidPolicy(true, resolvedSegments, source);
	}

	/**
	 * Returns Boolean.TRUE/FALSE when any level produces a concrete decision; null when
	 * every level is INHERIT (caller falls through to ORG_FLEXIBLE_DEFAULT).
	 *
	 * @throws RelizaException if real perspectives carry conflicting non-INHERIT overrides
	 */
	private Boolean decideEnabled(ComponentData cd, List<PerspectiveData> realPerspectives) throws RelizaException {
		SidPurlOverride compOverride = cd.getSidPurlOverride();
		if (compOverride != null && compOverride != SidPurlOverride.INHERIT) {
			return compOverride == SidPurlOverride.ENABLE;
		}

		// Component is INHERIT — consult real perspectives.
		Boolean perspectiveDecision = null;
		UUID decisionSource = null;
		for (PerspectiveData pd : realPerspectives) {
			SidPurlOverride pOverride = pd.getSidPurlOverride();
			if (pOverride == null || pOverride == SidPurlOverride.INHERIT) {
				continue;
			}
			boolean thisDecision = pOverride == SidPurlOverride.ENABLE;
			if (perspectiveDecision == null) {
				perspectiveDecision = thisDecision;
				decisionSource = pd.getUuid();
			} else if (!perspectiveDecision.equals(thisDecision)) {
				throw new RelizaException("Component " + cd.getUuid() + " belongs to multiple real perspectives "
						+ "with conflicting sidPurlOverride values: perspective " + decisionSource
						+ " says " + (perspectiveDecision ? "ENABLE" : "DISABLE")
						+ ", perspective " + pd.getUuid() + " says " + pOverride
						+ ". Resolve the conflict at the perspective level.");
			}
		}
		return perspectiveDecision;
	}

	/**
	 * Independent walk for authority segments — most-specific non-empty list wins.
	 *
	 * @throws RelizaException if real perspectives carry conflicting non-empty segments
	 */
	private List<String> decideSegments(ComponentData cd, List<PerspectiveData> realPerspectives,
			List<String> orgFallback) throws RelizaException {
		List<String> compSegments = cd.getSidAuthoritySegments();
		if (compSegments != null && !compSegments.isEmpty()) {
			return compSegments;
		}

		List<String> perspectiveSegments = null;
		UUID segmentsSource = null;
		for (PerspectiveData pd : realPerspectives) {
			List<String> pSegments = pd.getSidAuthoritySegments();
			if (pSegments == null || pSegments.isEmpty()) {
				continue;
			}
			if (perspectiveSegments == null) {
				perspectiveSegments = pSegments;
				segmentsSource = pd.getUuid();
			} else if (!perspectiveSegments.equals(pSegments)) {
				// List equality is order-sensitive on purpose: segments are an ordered
				// tuple (authority + publisher/BU/product-line context) and reordering
				// produces a different sid PURL canonicalization.
				throw new RelizaException("Component " + cd.getUuid() + " belongs to multiple real perspectives "
						+ "with conflicting sidAuthoritySegments: perspective " + segmentsSource
						+ " says " + perspectiveSegments + ", perspective " + pd.getUuid()
						+ " says " + pSegments
						+ ". Resolve the conflict at the perspective level.");
			}
		}
		if (perspectiveSegments != null) {
			return perspectiveSegments;
		}

		return orgFallback;
	}

	/**
	 * Real perspectives only (product-derived synthetics filtered out — they don't carry
	 * override fields). Single repo call regardless of perspective count. CE has no real
	 * perspectives so this returns empty there naturally.
	 */
	private List<PerspectiveData> collectRealPerspectives(ComponentData cd) {
		Set<UUID> perspectiveUuids = cd.getPerspectives();
		if (perspectiveUuids == null || perspectiveUuids.isEmpty()) {
			return List.of();
		}
		// findRealPerspectivesByUuids hits the perspective table directly and skips
		// the per-UUID product-derived fallback — exactly what we want, since only
		// real perspectives can carry sid override fields.
		List<PerspectiveData> realPerspectives = ossPerspectiveService.findRealPerspectivesByUuids(perspectiveUuids);
		// Defensive PerspectiveType filter in case the repository ever stores
		// product-derived rows directly (it doesn't today, but the type filter keeps
		// the contract local to this method).
		return realPerspectives.stream()
				.filter(pd -> pd.getType() == PerspectiveType.PERSPECTIVE)
				.toList();
	}
}
