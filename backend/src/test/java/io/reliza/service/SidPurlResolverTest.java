/**
* Copyright Reliza Incorporated. 2019 - 2026. Licensed under the terms of AGPL-3.0-only.
*/

package io.reliza.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.reliza.common.CommonVariables.PerspectiveType;
import io.reliza.common.CommonVariables.SidPurlMode;
import io.reliza.common.CommonVariables.SidPurlOverride;
import io.reliza.exceptions.RelizaException;
import io.reliza.model.ComponentData;
import io.reliza.model.DeliverableData.BelongsToOrganization;
import io.reliza.model.OrganizationData;
import io.reliza.model.saas.PerspectiveData;
import io.reliza.service.SidPurlResolver.ResolvedSidPolicy;
import io.reliza.service.SidPurlResolver.SidPurlSource;
import io.reliza.service.oss.OssPerspectiveService;

/**
 * Branch coverage for {@link SidPurlResolver}: EXTERNAL ceiling, org-mode short-circuit,
 * FLEXIBLE layered resolution (component / perspective / default), independent segments
 * walk, perspective ambiguity throws, orphan segments don't auto-enable.
 */
@ExtendWith(MockitoExtension.class)
public class SidPurlResolverTest {

	@Mock private OssPerspectiveService ossPerspectiveService;
	@InjectMocks private SidPurlResolver resolver;

	// ---- EXTERNAL hard ceiling ----

	@Test
	void externalComponent_disabledRegardlessOfOrgMode() throws RelizaException {
		ComponentData cd = component(BelongsToOrganization.EXTERNAL, null, null, null);
		// Try the most aggressive org config: STRICT + valid segments.
		OrganizationData org = orgWith(SidPurlMode.ENABLED_STRICT, List.of("acme.com"));

		ResolvedSidPolicy policy = resolver.resolveForComponent(cd, org);

		assertFalse(policy.enabled(), "EXTERNAL is a hard ceiling — STRICT must not enable it");
		assertEquals(SidPurlPolicySource(policy), SidPurlSource.EXTERNAL_COMPONENT);
		assertNull(policy.authoritySegments());
	}

	@Test
	void externalComponent_perspectiveEnableIgnored() throws RelizaException {
		// EXTERNAL beats even an explicit perspective ENABLE under FLEXIBLE.
		ComponentData cd = component(BelongsToOrganization.EXTERNAL, null, null,
				Set.of(UUID.randomUUID()));
		OrganizationData org = orgWith(SidPurlMode.ENABLED_FLEXIBLE, List.of("acme.com"));

		ResolvedSidPolicy policy = resolver.resolveForComponent(cd, org);

		assertFalse(policy.enabled());
		assertEquals(SidPurlSource.EXTERNAL_COMPONENT, policy.source());
	}

	// ---- Org mode short-circuit ----

	@Test
	void orgDisabled_returnsDisabled() throws RelizaException {
		ComponentData cd = component(BelongsToOrganization.INTERNAL, null, null, null);
		OrganizationData org = orgWith(SidPurlMode.DISABLED, null);

		ResolvedSidPolicy policy = resolver.resolveForComponent(cd, org);

		assertFalse(policy.enabled());
		assertEquals(SidPurlSource.ORG_DISABLED, policy.source());
		assertNull(policy.authoritySegments());
	}

	@Test
	void orgNullSettings_treatedAsDisabled() throws RelizaException {
		ComponentData cd = component(BelongsToOrganization.INTERNAL, null, null, null);
		OrganizationData org = new OrganizationData();
		// settings == null

		ResolvedSidPolicy policy = resolver.resolveForComponent(cd, org);

		assertFalse(policy.enabled());
		assertEquals(SidPurlSource.ORG_DISABLED, policy.source());
	}

	@Test
	void orgStrict_returnsEnabledWithOrgSegments() throws RelizaException {
		ComponentData cd = component(BelongsToOrganization.INTERNAL, null, null, null);
		OrganizationData org = orgWith(SidPurlMode.ENABLED_STRICT, List.of("reliza.io"));

		ResolvedSidPolicy policy = resolver.resolveForComponent(cd, org);

		assertTrue(policy.enabled());
		assertEquals(List.of("reliza.io"), policy.authoritySegments());
		assertEquals(SidPurlSource.ORG_STRICT, policy.source());
	}

	@Test
	void orgStrict_componentOverridesIgnoredAtResolveTime() throws RelizaException {
		// Even if a stale ENABLE/DISABLE got persisted, STRICT wins at resolve time.
		ComponentData cd = component(BelongsToOrganization.INTERNAL, SidPurlOverride.DISABLE,
				List.of("override.example"), null);
		OrganizationData org = orgWith(SidPurlMode.ENABLED_STRICT, List.of("reliza.io"));

		ResolvedSidPolicy policy = resolver.resolveForComponent(cd, org);

		assertTrue(policy.enabled());
		assertEquals(List.of("reliza.io"), policy.authoritySegments());
	}

	// ---- ENABLED_FLEXIBLE: layered resolution ----

	@Test
	void flexibleComponentEnable_winsOverOrgDefault() throws RelizaException {
		ComponentData cd = component(BelongsToOrganization.INTERNAL, SidPurlOverride.ENABLE,
				List.of("tenant.example"), null);
		OrganizationData org = orgWith(SidPurlMode.ENABLED_FLEXIBLE, List.of("reliza.io"));

		ResolvedSidPolicy policy = resolver.resolveForComponent(cd, org);

		assertTrue(policy.enabled());
		assertEquals(SidPurlSource.COMPONENT_OVERRIDE, policy.source());
		assertEquals(List.of("tenant.example"), policy.authoritySegments());
	}

	@Test
	void flexibleComponentDisable_overridesOrgDefault() throws RelizaException {
		ComponentData cd = component(BelongsToOrganization.INTERNAL, SidPurlOverride.DISABLE, null, null);
		OrganizationData org = orgWith(SidPurlMode.ENABLED_FLEXIBLE, List.of("reliza.io"));

		ResolvedSidPolicy policy = resolver.resolveForComponent(cd, org);

		assertFalse(policy.enabled());
		assertEquals(SidPurlSource.COMPONENT_OVERRIDE, policy.source());
	}

	@Test
	void flexibleNoOverrideAnywhere_disabledByDefault() throws RelizaException {
		ComponentData cd = component(BelongsToOrganization.INTERNAL, null, null, null);
		OrganizationData org = orgWith(SidPurlMode.ENABLED_FLEXIBLE, List.of("reliza.io"));

		ResolvedSidPolicy policy = resolver.resolveForComponent(cd, org);

		assertFalse(policy.enabled());
		assertEquals(SidPurlSource.ORG_FLEXIBLE_DEFAULT, policy.source());
	}

	@Test
	void flexiblePerspectiveEnable_winsWhenComponentInherits() throws RelizaException {
		UUID pUuid = UUID.randomUUID();
		ComponentData cd = component(BelongsToOrganization.INTERNAL, null, null, Set.of(pUuid));
		OrganizationData org = orgWith(SidPurlMode.ENABLED_FLEXIBLE, List.of("reliza.io"));
		when(ossPerspectiveService.findRealPerspectivesByUuids(anySet()))
				.thenReturn(List.of(perspective(pUuid, SidPurlOverride.ENABLE, null)));

		ResolvedSidPolicy policy = resolver.resolveForComponent(cd, org);

		assertTrue(policy.enabled());
		assertEquals(SidPurlSource.PERSPECTIVE_OVERRIDE, policy.source());
		// Segments fall back to org default since the perspective didn't supply its own.
		assertEquals(List.of("reliza.io"), policy.authoritySegments());
	}

	@Test
	void flexiblePerspectiveSegments_overrideOrgFallback() throws RelizaException {
		UUID pUuid = UUID.randomUUID();
		ComponentData cd = component(BelongsToOrganization.INTERNAL, null, null, Set.of(pUuid));
		OrganizationData org = orgWith(SidPurlMode.ENABLED_FLEXIBLE, List.of("reliza.io"));
		when(ossPerspectiveService.findRealPerspectivesByUuids(anySet()))
				.thenReturn(List.of(perspective(pUuid, SidPurlOverride.ENABLE,
						List.of("tenant.example.com", "Acme Robotics"))));

		ResolvedSidPolicy policy = resolver.resolveForComponent(cd, org);

		assertTrue(policy.enabled());
		assertEquals(List.of("tenant.example.com", "Acme Robotics"), policy.authoritySegments());
	}

	// ---- Independent segments walk: component beats perspective beats org ----

	@Test
	void componentSegments_winOverPerspectiveAndOrg() throws RelizaException {
		// Component override=ENABLE + own segments — those take precedence over both
		// perspective segments and org fallback.
		UUID pUuid = UUID.randomUUID();
		ComponentData cd = component(BelongsToOrganization.INTERNAL, SidPurlOverride.ENABLE,
				List.of("comp.example.com"), Set.of(pUuid));
		OrganizationData org = orgWith(SidPurlMode.ENABLED_FLEXIBLE, List.of("reliza.io"));
		when(ossPerspectiveService.findRealPerspectivesByUuids(anySet()))
				.thenReturn(List.of(perspective(pUuid, SidPurlOverride.ENABLE,
						List.of("persp.example"))));

		ResolvedSidPolicy policy = resolver.resolveForComponent(cd, org);

		assertEquals(List.of("comp.example.com"), policy.authoritySegments());
	}

	// ---- Perspective ambiguity ----

	@Test
	void perspectiveOverrideMismatch_throws() {
		UUID p1 = UUID.randomUUID();
		UUID p2 = UUID.randomUUID();
		ComponentData cd = component(BelongsToOrganization.INTERNAL, null, null, Set.of(p1, p2));
		OrganizationData org = orgWith(SidPurlMode.ENABLED_FLEXIBLE, List.of("reliza.io"));
		when(ossPerspectiveService.findRealPerspectivesByUuids(anySet()))
				.thenReturn(List.of(
						perspective(p1, SidPurlOverride.ENABLE, null),
						perspective(p2, SidPurlOverride.DISABLE, null)));

		assertThrows(RelizaException.class, () -> resolver.resolveForComponent(cd, org));
	}

	@Test
	void perspectiveSegmentsMismatchWhenBothEnable_throws() {
		UUID p1 = UUID.randomUUID();
		UUID p2 = UUID.randomUUID();
		ComponentData cd = component(BelongsToOrganization.INTERNAL, null, null, Set.of(p1, p2));
		OrganizationData org = orgWith(SidPurlMode.ENABLED_FLEXIBLE, List.of("reliza.io"));
		when(ossPerspectiveService.findRealPerspectivesByUuids(anySet()))
				.thenReturn(List.of(
						perspective(p1, SidPurlOverride.ENABLE, List.of("a.example.com")),
						perspective(p2, SidPurlOverride.ENABLE, List.of("b.example.com"))));

		assertThrows(RelizaException.class, () -> resolver.resolveForComponent(cd, org));
	}

	@Test
	void perspectiveSegmentsAgree_acceptedEvenAcrossMultiplePerspectives() throws RelizaException {
		// Both perspectives ENABLE with the same segments — equality check passes.
		UUID p1 = UUID.randomUUID();
		UUID p2 = UUID.randomUUID();
		ComponentData cd = component(BelongsToOrganization.INTERNAL, null, null, Set.of(p1, p2));
		OrganizationData org = orgWith(SidPurlMode.ENABLED_FLEXIBLE, List.of("reliza.io"));
		when(ossPerspectiveService.findRealPerspectivesByUuids(anySet()))
				.thenReturn(List.of(
						perspective(p1, SidPurlOverride.ENABLE, List.of("shared.example.com")),
						perspective(p2, SidPurlOverride.ENABLE, List.of("shared.example.com"))));

		ResolvedSidPolicy policy = resolver.resolveForComponent(cd, org);

		assertTrue(policy.enabled());
		assertEquals(List.of("shared.example.com"), policy.authoritySegments());
	}

	// ---- Orphan segments at component level ----

	@Test
	void componentOrphanSegments_doNotAutoEnable() throws RelizaException {
		// Component has segments but override=INHERIT, no perspective override → still disabled.
		// Segments are inherited up the chain only if an upper level enables.
		ComponentData cd = component(BelongsToOrganization.INTERNAL, null,
				List.of("orphan.example.com"), null);
		OrganizationData org = orgWith(SidPurlMode.ENABLED_FLEXIBLE, List.of("reliza.io"));

		ResolvedSidPolicy policy = resolver.resolveForComponent(cd, org);

		assertFalse(policy.enabled(), "orphan component segments must not auto-enable sid");
		assertEquals(SidPurlSource.ORG_FLEXIBLE_DEFAULT, policy.source());
	}

	// ---- Helpers ----

	private static ComponentData component(BelongsToOrganization isInternal, SidPurlOverride override,
			List<String> segments, Set<UUID> perspectives) {
		ComponentData cd = new ComponentData();
		cd.setUuid(UUID.randomUUID());
		cd.setName("TestComponent");
		cd.setIsInternal(isInternal);
		cd.setSidPurlOverride(override);
		cd.setSidAuthoritySegments(segments);
		cd.setPerspectives(perspectives);
		return cd;
	}

	private static PerspectiveData perspective(UUID uuid, SidPurlOverride override, List<String> segments) {
		PerspectiveData pd = new PerspectiveData();
		pd.setUuid(uuid);
		pd.setName("p-" + uuid.toString().substring(0, 8));
		pd.setType(PerspectiveType.PERSPECTIVE);
		pd.setSidPurlOverride(override);
		pd.setSidAuthoritySegments(segments);
		return pd;
	}

	private static OrganizationData orgWith(SidPurlMode mode, List<String> segments) {
		OrganizationData od = new OrganizationData();
		od.setUuid(UUID.randomUUID());
		OrganizationData.Settings s = new OrganizationData.Settings();
		s.setSidPurlMode(mode);
		s.setSidAuthoritySegments(segments);
		od.setSettings(s);
		return od;
	}

	/** Workaround for the resolver returning a record whose {@code source()} is the SidPurlSource enum. */
	private static SidPurlSource SidPurlPolicySource(ResolvedSidPolicy policy) {
		return policy.source();
	}
}
