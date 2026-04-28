/**
* Copyright Reliza Incorporated. 2019 - 2026. Licensed under the terms of AGPL-3.0-only.
*/

package io.reliza.service.oss;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Service;

import io.reliza.common.CommonVariables.PerspectiveType;
import io.reliza.model.RelizaObject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OssPerspectiveService {
	
	public static class PerspectiveData implements RelizaObject {
		public PerspectiveType getType() {
			return null;
		}

		@Override
		public UUID getUuid() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public UUID getOrg() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public UUID getResourceGroup() {
			// TODO Auto-generated method stub
			return null;
		}
	}
	/**
	 * Part of ReARM Pro only
	 * @param uuid
	 * @return
	 */
	public Optional<PerspectiveData> getPerspectiveData (UUID uuid) {
		return Optional.empty();
	}

	/**
	 * Fetch real perspectives by UUID in a single repo call. No fallback to product-derived
	 * synthetics — UUIDs not in the perspective table are silently dropped. Use this on
	 * hot paths that walk {@code ComponentData.perspectives}.
	 */
	public List<PerspectiveData> findRealPerspectivesByUuids(Set<UUID> uuids) {
		if (uuids == null || uuids.isEmpty()) {
			return List.of();
		}
		return StreamSupport.stream(repository.findAllById(uuids).spliterator(), false)
				.map(PerspectiveData::dataFromRecord)
				.collect(Collectors.toUnmodifiableList());
	}
}
