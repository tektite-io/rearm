/**
* Copyright Reliza Incorporated. 2019 - 2026. Licensed under the terms of AGPL-3.0-only.
*/

package io.reliza.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Pin {@link Utils#stripContainerTagAndDigest(String)}.
 *
 * <p>Used on the obom-export side to canonicalise container display
 * identifiers before they're emitted as CycloneDX {@code component.name}.
 * Multiple ingestion paths into ReARM leave deliverable display
 * identifiers with {@code :tag} (and sometimes {@code @digest}) baked
 * in; without normalisation the emitter writes
 * {@code image:tag} as {@code name} while also setting {@code version}
 * separately, producing {@code image:tag:tag}-style duplication
 * downstream. Reported by user against a helm-derived obom whose redis
 * component had {@code name: "docker.io/library/redis:8.6.2-alpine3.23:8.6.2-alpine3.23"}.
 *
 * <p>Conservative against hostnames that include a port — only the
 * colon after the last forward-slash counts as a tag separator, so
 * {@code registry.example.com:5000/foo:1.0} trims to
 * {@code registry.example.com:5000/foo} (port preserved). Idempotent.
 */
class UtilsStripContainerTagTest {

	@Test
	void stripsTrailingTag() {
		assertEquals("docker.io/library/redis",
				Utils.stripContainerTagAndDigest("docker.io/library/redis:8.6.2-alpine3.23"));
	}

	@Test
	void stripsTrailingDigest() {
		assertEquals("registry.example.com/foo",
				Utils.stripContainerTagAndDigest("registry.example.com/foo@sha256:abcd"));
	}

	@Test
	void stripsTagAndDigest() {
		assertEquals("registry.example.com/foo",
				Utils.stripContainerTagAndDigest("registry.example.com/foo:1.0@sha256:abcd"));
	}

	@Test
	void stripsDoubleTagPattern() {
		// The reported bug shape — handled by the same logic as a normal
		// trailing-tag strip because indexOf(':', lastSlash) catches the
		// first colon after the last slash and substring()s up to there,
		// so any number of trailing :tag occurrences are removed in one shot.
		assertEquals("docker.io/library/redis",
				Utils.stripContainerTagAndDigest("docker.io/library/redis:8.6.2-alpine3.23:8.6.2-alpine3.23"));
	}

	@Test
	void preservesPortInHostnameWithoutTag() {
		assertEquals("registry.example.com:5000/foo",
				Utils.stripContainerTagAndDigest("registry.example.com:5000/foo"));
	}

	@Test
	void preservesPortStripsTrailingTag() {
		assertEquals("registry.example.com:5000/foo",
				Utils.stripContainerTagAndDigest("registry.example.com:5000/foo:1.0"));
	}

	@Test
	void preservesPortStripsDigest() {
		assertEquals("registry.example.com:5000/foo",
				Utils.stripContainerTagAndDigest("registry.example.com:5000/foo@sha256:abcd"));
	}

	@Test
	void noOpWhenNoTagOrDigest() {
		assertEquals("docker.io/library/redis",
				Utils.stripContainerTagAndDigest("docker.io/library/redis"));
	}

	@Test
	void stripsBareTag() {
		// Un-namespaced names ("redis:8.6.2") still strip — there's no
		// slash to anchor the search but the conservative bound falls back
		// to position 0, so the first colon counts as a tag separator.
		assertEquals("redis",
				Utils.stripContainerTagAndDigest("redis:8.6.2"));
	}

	@Test
	void stripsBareDigest() {
		assertEquals("redis",
				Utils.stripContainerTagAndDigest("redis@sha256:abcd"));
	}

	@Test
	void idempotent() {
		String once = Utils.stripContainerTagAndDigest("docker.io/library/redis:8.6.2-alpine3.23:8.6.2-alpine3.23");
		String twice = Utils.stripContainerTagAndDigest(once);
		assertEquals(once, twice);
	}

	@Test
	void nullAndEmptyAreNoOp() {
		assertNull(Utils.stripContainerTagAndDigest(null));
		assertEquals("", Utils.stripContainerTagAndDigest(""));
	}
}
