/**
* Copyright Reliza Incorporated. 2019 - 2026. Licensed under the terms of AGPL-3.0-only.
*/
package io.reliza.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import io.reliza.model.SbomComponent;

public interface SbomComponentRepository extends CrudRepository<SbomComponent, UUID> {

	Optional<SbomComponent> findByOrgAndCanonicalPurl(UUID org, String canonicalPurl);

	@Query(
		value = "SELECT * FROM rearm.sbom_components WHERE org = CAST(:orgUuidAsString AS uuid) AND canonical_purl IN (:canonicalPurls)",
		nativeQuery = true)
	List<SbomComponent> findByOrgAndCanonicalPurlIn(
			@Param("orgUuidAsString") String orgUuidAsString,
			@Param("canonicalPurls") Collection<String> canonicalPurls);

	/**
	 * Search canonical sbom_components scoped to an org. With per-org pinning
	 * the org filter is a direct column match — no join through
	 * release_sbom_components. Version filter is optional; pass null to match
	 * any version.
	 */
	@Query(
		value = """
			SELECT *
			FROM rearm.sbom_components
			WHERE org = CAST(:orgUuidAsString AS uuid)
			AND record_data->>'name' = :name
			AND (CAST(:version AS text) IS NULL OR record_data->>'version' = :version)
		""",
		nativeQuery = true)
	List<SbomComponent> searchByOrgAndNameAndOptionalVersion(
			@Param("orgUuidAsString") String orgUuidAsString,
			@Param("name") String name,
			@Param("version") String version);
}
