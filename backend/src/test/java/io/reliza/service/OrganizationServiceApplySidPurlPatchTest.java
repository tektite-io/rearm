/**
* Copyright Reliza Incorporated. 2019 - 2026. Licensed under the terms of AGPL-3.0-only.
*/

package io.reliza.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.reliza.common.CommonVariables.SidPurlMode;
import io.reliza.model.OrganizationData;
import io.reliza.repositories.OrganizationRepository;

/**
 * Tests for {@code OrganizationService.applySidPurlPatch}: validates segments whenever
 * supplied (regardless of mode) and clears stored segments when effective mode resolves
 * to DISABLED — closes paths where invalid segments could be persisted while sid is off
 * and "go live" later when an admin flips the mode.
 */
public class OrganizationServiceApplySidPurlPatchTest {

	private OrganizationService service;
	private Method applyPatch;

	@BeforeEach
	void setUp() throws Exception {
		// applySidPurlPatch is purely a function of (settings, patch) — no autowired deps
		// touched. We use a minimal repository stub since the constructor signature
		// requires it but the method doesn't reach for it.
		service = new OrganizationService((OrganizationRepository) null);
		applyPatch = OrganizationService.class.getDeclaredMethod(
				"applySidPurlPatch", OrganizationData.Settings.class, OrganizationData.Settings.class);
		applyPatch.setAccessible(true);
	}

	// ---- Validation gates (review #2 fix) ----

	@Test
	void invalidSegmentsRejectedEvenWhenModeIsNull() throws Exception {
		// Pristine org: settings.sidPurlMode == null. Patch carries invalid segments.
		// Old behavior: validation skipped (effectiveMode == null) → invalid persisted.
		// Fix: validation runs because patchSegments != null.
		OrganizationData.Settings settings = new OrganizationData.Settings();
		OrganizationData.Settings patch = new OrganizationData.Settings();
		patch.setSidAuthoritySegments(List.of("192.168.1.1")); // looks like IPv4 → invalid

		Throwable cause = assertThrows(java.lang.reflect.InvocationTargetException.class,
				() -> applyPatch.invoke(service, settings, patch)).getTargetException();
		assertEquals(io.reliza.exceptions.RelizaException.class, cause.getClass());
	}

	@Test
	void invalidSegmentsRejectedEvenWhenModeIsDisabled() throws Exception {
		OrganizationData.Settings settings = new OrganizationData.Settings();
		settings.setSidPurlMode(SidPurlMode.DISABLED);
		OrganizationData.Settings patch = new OrganizationData.Settings();
		patch.setSidAuthoritySegments(List.of(" leading-space.example.com")); // leading whitespace

		Throwable cause = assertThrows(java.lang.reflect.InvocationTargetException.class,
				() -> applyPatch.invoke(service, settings, patch)).getTargetException();
		assertEquals(io.reliza.exceptions.RelizaException.class, cause.getClass());
	}

	@Test
	void emptySegmentsListSkipsValidation() throws Exception {
		// An explicit empty list is allowed (it's the "clear segments" signal under DISABLED).
		OrganizationData.Settings settings = new OrganizationData.Settings();
		settings.setSidPurlMode(SidPurlMode.DISABLED);
		OrganizationData.Settings patch = new OrganizationData.Settings();
		patch.setSidAuthoritySegments(List.of());

		applyPatch.invoke(service, settings, patch); // no throw
	}

	// ---- Clear-segments rule (review #2 fix) ----

	@Test
	void nullModeWithSegments_clearedAfterApply() throws Exception {
		// Pristine org with null mode and segments persisted by some legacy path.
		// getSidPurlModeOrDefault() treats null as DISABLED, so the "clear segments
		// when DISABLED" rule should fire. Old code only fired when mode was explicitly
		// DISABLED (not when null).
		OrganizationData.Settings settings = new OrganizationData.Settings();
		// Stale segments stored in some prior state — never validated under old code.
		settings.setSidAuthoritySegments(List.of("legacy.example.com"));
		// Patch is a no-op.
		OrganizationData.Settings patch = new OrganizationData.Settings();

		applyPatch.invoke(service, settings, patch);

		assertNull(settings.getSidAuthoritySegments(),
				"DISABLED-by-default (null mode) must clear stored segments after apply");
	}

	@Test
	void explicitDisabledClearsSegments() throws Exception {
		OrganizationData.Settings settings = new OrganizationData.Settings();
		settings.setSidPurlMode(SidPurlMode.ENABLED_FLEXIBLE);
		settings.setSidAuthoritySegments(List.of("acme.com"));
		// Patch flips to DISABLED.
		OrganizationData.Settings patch = new OrganizationData.Settings();
		patch.setSidPurlMode(SidPurlMode.DISABLED);

		applyPatch.invoke(service, settings, patch);

		assertEquals(SidPurlMode.DISABLED, settings.getSidPurlMode());
		assertNull(settings.getSidAuthoritySegments(),
				"DISABLED implies no segments — must clear regardless of patch content");
	}

	// ---- Happy paths preserved ----

	@Test
	void enabledStrictWithValidSegments_persisted() throws Exception {
		OrganizationData.Settings settings = new OrganizationData.Settings();
		OrganizationData.Settings patch = new OrganizationData.Settings();
		patch.setSidPurlMode(SidPurlMode.ENABLED_STRICT);
		patch.setSidAuthoritySegments(List.of("reliza.io"));

		applyPatch.invoke(service, settings, patch);

		assertEquals(SidPurlMode.ENABLED_STRICT, settings.getSidPurlMode());
		assertEquals(List.of("reliza.io"), settings.getSidAuthoritySegments());
	}

	@Test
	void enabledStrictWithoutSegments_throws() throws Exception {
		OrganizationData.Settings settings = new OrganizationData.Settings();
		OrganizationData.Settings patch = new OrganizationData.Settings();
		patch.setSidPurlMode(SidPurlMode.ENABLED_STRICT);
		// No segments — neither in patch nor on stored settings.

		Throwable cause = assertThrows(java.lang.reflect.InvocationTargetException.class,
				() -> applyPatch.invoke(service, settings, patch)).getTargetException();
		assertEquals(io.reliza.exceptions.RelizaException.class, cause.getClass());
	}

	@Test
	void enabledFlexibleWithoutSegments_acceptedAsOptional() throws Exception {
		OrganizationData.Settings settings = new OrganizationData.Settings();
		OrganizationData.Settings patch = new OrganizationData.Settings();
		patch.setSidPurlMode(SidPurlMode.ENABLED_FLEXIBLE);
		// No segments — OK, FLEXIBLE accepts components/perspectives supplying their own.

		applyPatch.invoke(service, settings, patch); // no throw

		assertEquals(SidPurlMode.ENABLED_FLEXIBLE, settings.getSidPurlMode());
		assertNull(settings.getSidAuthoritySegments());
	}
}
