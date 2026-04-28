/**
* Copyright Reliza Incorporated. 2019 - 2026. Licensed under the terms of AGPL-3.0-only.
*/

package io.reliza.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.reliza.common.CommonVariables.SidPurlMode;
import io.reliza.exceptions.RelizaException;
import io.reliza.model.OrganizationData;
import io.reliza.repositories.OrganizationRepository;
import io.reliza.service.OrganizationService;

/**
 * Pin the contract that {@code applySidPurlPatch} throws {@link RelizaException}
 * directly (the {@code GraphQLExceptionHandlers#handleReliza} branch then carries
 * the validation message to tenants), not an unmapped exception that falls through
 * to the generic "Internal server error" handler.
 */
public class OrganizationDataFetcherSidErrorMappingTest {

	private OrganizationService service;

	@BeforeEach
	void setUp() throws Exception {
		// Constructor is package-private; reach via reflection.
		var ctor = OrganizationService.class.getDeclaredConstructor(OrganizationRepository.class);
		ctor.setAccessible(true);
		service = ctor.newInstance((OrganizationRepository) null);
	}

	@Test
	void invalidPatchSegments_throwRelizaException() throws Exception {
		// applySidPurlPatch throws RelizaException directly for invalid segments —
		// updateSettings' outer catch block rethrows it, and the @GraphQlExceptionHandler
		// in GraphQLExceptionHandlers maps it to a tenant-readable error with the
		// validation message intact.
		var applyPatch = OrganizationService.class.getDeclaredMethod(
				"applySidPurlPatch", OrganizationData.Settings.class, OrganizationData.Settings.class);
		applyPatch.setAccessible(true);

		OrganizationData.Settings settings = new OrganizationData.Settings();
		OrganizationData.Settings patch = new OrganizationData.Settings();
		patch.setSidPurlMode(SidPurlMode.ENABLED_STRICT);
		patch.setSidAuthoritySegments(List.of("192.168.1.1"));

		Throwable cause = assertThrows(java.lang.reflect.InvocationTargetException.class,
				() -> applyPatch.invoke(service, settings, patch)).getTargetException();
		assertEquals(RelizaException.class, cause.getClass(),
				"applySidPurlPatch must throw RelizaException directly so updateSettings' " +
				"RelizaException rethrow preserves the validation message for the tenant");
		assertTrue(cause.getMessage().toLowerCase().contains("ipv4"),
				"validation message should reach the tenant; got: " + cause.getMessage());
	}

	@SuppressWarnings("unused")
	private void unused() {
		Map.of();
	}
}
