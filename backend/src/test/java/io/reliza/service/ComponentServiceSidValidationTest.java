/**
* Copyright Reliza Incorporated. 2019 - 2026. Licensed under the terms of AGPL-3.0-only.
*/

package io.reliza.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import io.reliza.common.CommonVariables.SidPurlMode;
import io.reliza.common.CommonVariables.SidPurlOverride;
import io.reliza.exceptions.RelizaException;
import io.reliza.model.ComponentData;
import io.reliza.model.DeliverableData.BelongsToOrganization;
import io.reliza.model.OrganizationData;
import io.reliza.model.dto.CreateComponentDto;

/**
 * Component sid plumbing: {@code getIsInternalOrDefault} back-compat default; factory
 * normalization on create; private {@code validateSidOverrideAgainstOrg} write-time gate.
 */
@ExtendWith(MockitoExtension.class)
public class ComponentServiceSidValidationTest {

	@Mock private GetOrganizationService getOrganizationService;
	@Mock private io.reliza.repositories.ComponentRepository componentRepository;

	private ComponentService service;

	/**
	 * {@link ComponentService} has a constructor taking {@code ComponentRepository} and a
	 * {@code @Value} String — Mockito's {@code @InjectMocks} can't satisfy the second arg.
	 * Build the service manually and reflectively wire the dependency we actually exercise.
	 */
	@BeforeEach
	void setUp() throws Exception {
		var ctor = ComponentService.class.getDeclaredConstructor(
				io.reliza.repositories.ComponentRepository.class, String.class);
		ctor.setAccessible(true);
		service = ctor.newInstance(componentRepository, "http://test/");
		var f = ComponentService.class.getDeclaredField("getOrganizationService");
		f.setAccessible(true);
		f.set(service, getOrganizationService);
	}

	// ---- ComponentData.getIsInternalOrDefault ----

	@Test
	void getIsInternalOrDefault_nullDefaultsToInternal() {
		ComponentData cd = new ComponentData();
		// isInternal not set — represents legacy rows.
		assertEquals(BelongsToOrganization.INTERNAL, cd.getIsInternalOrDefault());
	}

	@Test
	void getIsInternalOrDefault_externalReturnedAsIs() {
		ComponentData cd = new ComponentData();
		cd.setIsInternal(BelongsToOrganization.EXTERNAL);
		assertEquals(BelongsToOrganization.EXTERNAL, cd.getIsInternalOrDefault());
	}

	@Test
	void getIsInternalOrDefault_internalReturnedAsIs() {
		ComponentData cd = new ComponentData();
		cd.setIsInternal(BelongsToOrganization.INTERNAL);
		assertEquals(BelongsToOrganization.INTERNAL, cd.getIsInternalOrDefault());
	}

	// ---- componentDataFactory: defaults + normalization on create ----

	@Test
	void componentDataFactory_omittedIsInternal_defaultsToInternalOnCreate() {
		// Pre-existing clients that don't know about the field keep working — server stamps INTERNAL.
		CreateComponentDto cpd = createDto(/* isInternal */ null, /* override */ null);
		ComponentData cd = ComponentData.componentDataFactory(cpd);
		assertEquals(BelongsToOrganization.INTERNAL, cd.getIsInternal(),
				"server-side default-INTERNAL on create");
	}

	@Test
	void componentDataFactory_externalIsInternal_preservedOnCreate() {
		CreateComponentDto cpd = createDto(BelongsToOrganization.EXTERNAL, null);
		ComponentData cd = ComponentData.componentDataFactory(cpd);
		assertEquals(BelongsToOrganization.EXTERNAL, cd.getIsInternal());
	}

	@Test
	void componentDataFactory_inheritOverride_normalizedToNull() {
		// INHERIT clears the override (defers to higher level) — store as null for cleaner record.
		CreateComponentDto cpd = createDto(null, SidPurlOverride.INHERIT);
		ComponentData cd = ComponentData.componentDataFactory(cpd);
		assertNull(cd.getSidPurlOverride(), "INHERIT must normalize to null");
	}

	@Test
	void componentDataFactory_enableOverride_preserved() {
		CreateComponentDto cpd = createDto(null, SidPurlOverride.ENABLE);
		ComponentData cd = ComponentData.componentDataFactory(cpd);
		assertEquals(SidPurlOverride.ENABLE, cd.getSidPurlOverride());
	}

	// ---- ComponentService.validateSidOverrideAgainstOrg ----

	@Test
	void validateSidOverrideAgainstOrg_inherit_alwaysAllowed() throws Exception {
		// INHERIT/null short-circuit — no org lookup needed.
		invokeValidate(orgUuid(), null); // no throw
		invokeValidate(orgUuid(), SidPurlOverride.INHERIT); // no throw
		// Mock not called — verifyZeroInteractions equivalent isn't strict in v5; rely on no NPE.
	}

	@Test
	void validateSidOverrideAgainstOrg_enableUnderFlexible_allowed() throws Exception {
		UUID org = orgUuid();
		stubOrgMode(org, SidPurlMode.ENABLED_FLEXIBLE);
		assertDoesNotThrow(() -> invokeValidate(org, SidPurlOverride.ENABLE));
	}

	@Test
	void validateSidOverrideAgainstOrg_disableUnderFlexible_allowed() throws Exception {
		UUID org = orgUuid();
		stubOrgMode(org, SidPurlMode.ENABLED_FLEXIBLE);
		assertDoesNotThrow(() -> invokeValidate(org, SidPurlOverride.DISABLE));
	}

	@Test
	void validateSidOverrideAgainstOrg_enableUnderStrict_rejected() {
		UUID org = orgUuid();
		stubOrgMode(org, SidPurlMode.ENABLED_STRICT);
		Throwable cause = assertThrows(java.lang.reflect.InvocationTargetException.class,
				() -> invokeValidate(org, SidPurlOverride.ENABLE)).getTargetException();
		assertTrue(cause instanceof RelizaException, "expected RelizaException, got " + cause);
		assertNotNull(cause.getMessage());
		assertTrue(cause.getMessage().contains("ENABLED_FLEXIBLE"),
				"error message must point operators at the org-level fix");
	}

	@Test
	void validateSidOverrideAgainstOrg_disableUnderDisabledOrg_rejected() {
		UUID org = orgUuid();
		stubOrgMode(org, SidPurlMode.DISABLED);
		Throwable cause = assertThrows(java.lang.reflect.InvocationTargetException.class,
				() -> invokeValidate(org, SidPurlOverride.DISABLE)).getTargetException();
		assertTrue(cause instanceof RelizaException);
	}

	@Test
	void validateSidOverrideAgainstOrg_enableWhenOrgNotFound_rejected() {
		// Defensive: missing org metadata defaults to DISABLED, so ENABLE/DISABLE rejected.
		UUID org = orgUuid();
		Mockito.when(getOrganizationService.getOrganizationData(org))
				.thenReturn(Optional.empty());
		Throwable cause = assertThrows(java.lang.reflect.InvocationTargetException.class,
				() -> invokeValidate(org, SidPurlOverride.ENABLE)).getTargetException();
		assertTrue(cause instanceof RelizaException);
	}

	// ---- Helpers ----

	private static UUID orgUuid() {
		return UUID.randomUUID();
	}

	private void stubOrgMode(UUID org, SidPurlMode mode) {
		OrganizationData od = new OrganizationData();
		OrganizationData.Settings s = new OrganizationData.Settings();
		s.setSidPurlMode(mode);
		od.setSettings(s);
		Mockito.when(getOrganizationService.getOrganizationData(org))
				.thenReturn(Optional.of(od));
	}

	private void invokeValidate(UUID org, SidPurlOverride override) throws Exception {
		Method m = ComponentService.class.getDeclaredMethod(
				"validateSidOverrideAgainstOrg", UUID.class, SidPurlOverride.class);
		m.setAccessible(true);
		m.invoke(service, org, override);
	}

	private static CreateComponentDto createDto(BelongsToOrganization isInternal, SidPurlOverride override) {
		return CreateComponentDto.builder()
				.name("Test")
				.organization(UUID.randomUUID())
				.type(ComponentData.ComponentType.COMPONENT)
				.isInternal(isInternal)
				.sidPurlOverride(override)
				.build();
	}
}
