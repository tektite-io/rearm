/**
* Copyright Reliza Incorporated. 2019 - 2026. Licensed under the terms of AGPL-3.0-only.
*/

package io.reliza.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.reliza.common.CommonVariables.SidPurlMode;
import io.reliza.common.CommonVariables.SidPurlOverride;
import io.reliza.exceptions.RelizaException;
import io.reliza.model.ComponentData;
import io.reliza.model.DeliverableData.BelongsToOrganization;
import io.reliza.model.OrganizationData;
import io.reliza.service.oss.OssPerspectiveService;
import io.reliza.service.oss.OssPerspectiveService.PerspectiveSidResolution;

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

		// Perspective walk + conflict detection lives in OssPerspectiveService — keeps SaaS-only
		// PerspectiveData out of this file. CE returns a vacuous resolution.
		PerspectiveSidResolution perspectiveOverrides = ossPerspectiveService.resolvePerspectiveSidOverrides(cd);

		SidPurlOverride componentOverride = cd.getSidPurlOverride();
		Boolean enabled;
		SidPurlSource source;
		if (componentOverride != null && componentOverride != SidPurlOverride.INHERIT) {
			enabled = componentOverride == SidPurlOverride.ENABLE;
			source = SidPurlSource.COMPONENT_OVERRIDE;
		} else if (perspectiveOverrides.enabled() != null) {
			enabled = perspectiveOverrides.enabled();
			source = SidPurlSource.PERSPECTIVE_OVERRIDE;
		} else {
			return new ResolvedSidPolicy(false, null, SidPurlSource.ORG_FLEXIBLE_DEFAULT);
		}

		if (Boolean.FALSE.equals(enabled)) {
			return new ResolvedSidPolicy(false, null, source);
		}

		List<String> resolvedSegments = decideSegments(cd, perspectiveOverrides.segments(), orgSegments);
		return new ResolvedSidPolicy(true, resolvedSegments, source);
	}

	/** Most-specific non-empty list wins: component → perspective → org. */
	private static List<String> decideSegments(ComponentData cd, List<String> perspectiveSegments,
			List<String> orgFallback) {
		List<String> compSegments = cd.getSidAuthoritySegments();
		if (compSegments != null && !compSegments.isEmpty()) {
			return compSegments;
		}
		if (perspectiveSegments != null && !perspectiveSegments.isEmpty()) {
			return perspectiveSegments;
		}
		return orgFallback;
	}
}
