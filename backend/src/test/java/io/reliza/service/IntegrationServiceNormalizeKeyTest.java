/**
* Copyright Reliza Incorporated. 2019 - 2026. Licensed under the terms of AGPL-3.0-only.
*/

package io.reliza.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class IntegrationServiceNormalizeKeyTest {

	private static final String DER_BASE64 =
			"MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDOg/te0taAGDli"
			+ "ozGt93gS1AzYwb+jfr89ojjhj87xWmmgipjA9QTgz5PAK3K+8fDknRItbkICe5wd"
			+ "fdREXKuNz7Y0+nzabGfYqaVrUiY0HmzfCCjWoz0u/5TvLGBLh6hHhvG4GjJ9FLQS"
			+ "Rff/WH9tklnqqBfGgtmwZJSI5mphDOPx73/K5Ow8VVur1sB9N2qi34oBeZJuv9xt"
			+ "+JYsQbtNBmgIxzp0AjPySwX2quTPpsnSrLjMc1TeUhm/kM1Jk0e7CZzIZcKcaKxv"
			+ "c1lR+cUr8HX0ZqGfLWN/CPTK0KNVwxxo80Nxg0p9M796xhljjHFUtMIQJSjI5wyR"
			+ "wCkRpGllAgMBAAECggEAAL3odir84m6+grGc4P0u/9GmA9T7AVVCzm/J01pET0l4"
			+ "mynG9Nx8dRVHIy0qHJr7DKCEJfMhMWfsbo5a5+2gSnezoHBCPg5JIjYGdi1QSKbP"
			+ "qSPFnR3EKhTOHhIop2TNmzIre0fmz1xhbYu1oW5vM2ok62BJXj5uleu1PTenVqwR"
			+ "nPxo0dqlry1mU0Z+HOhS0hiPJP1ZUbvp7GVsjW07Q5uUO05ZnUFKsuKmFQ1MJoPE"
			+ "rKvcLQXFKA9mCH3jC+/7k7TiQjj/WWG+qOsbZcCQAswfZJoCnd58GA3mlYj1nwpw"
			+ "zconegYJSZQmT6PQrk8cTXn71iW4RSPrtYhYuqsj8QKBgQDnfo5SCsabjUUufoHU"
			+ "2kTJwqyLmm6ocBYinp8Aj8sik0EOTOWgunGI2AkZO2yfnUQW+13IreYQMAeu5ycj"
			+ "5c7yPWnzUfxYMm+PU60x8+YwOgFxgfIDvsNpJ4o+2u9+ibeE57Deg1dC3JHly8dv"
			+ "z540yiiJQSfIBpaZWTdXBz8hEQKBgQDkYIGPVTkv+tICNt0Pz8hsZ3GIF03/vm6m"
			+ "5VrosLmg9zND7dB4LAduGrH3NM3dAebjTCt9nQZSjIU38iUck6w2p83qaVscQAE5"
			+ "YrXzr61wfLVEXKGCAabS4T4KAty290tI9ZHZi6eRhBCd1OrrxrkaSHJTq61zZcP4"
			+ "729AwtuDFQKBgGtXlrzaPE/iyOKPTLqbX2xC9elh+c/m8YyqCsai5qmoqHDuHUhT"
			+ "S9QNgOKPWIYG0YzqkAk/AcAd1WI300FlDXnsGlX2fVGUSYCnZ1cTZniUXRj6DXPJ"
			+ "ICmBaSxOLuF5EHzzRmNXeb4KMyjgPiFNn2mRGAzVBpJM48ZFxVB4jsBRAoGAete+"
			+ "WQ8Kd9h+5loEhuDJeiYaV43RkuMoOfXc9JU7BG5z0PI21K06QetyFvr8UQkY3OK3"
			+ "8hVotTaI4hMKC1kTpUKV7KVHMObswaVrIe05aexqxJ7e8UNaLMyjxlbLyk8y28fb"
			+ "8BzCwe870Ooag0CdiJm3hXIvFoeC5oBEY51s3hECgYBWtWhXE2yMtxvoElpW7ghT"
			+ "RNJh+1KOXCoa/r4Ct1dMxRtI0KfTTZNXKvKUvTiBRi4qsyrEILc2IWBbkxz6xxI5"
			+ "MvgjN/PSXGvd1stYNeLFqAa3aZ23YRUdNMUvdPoh9cyyE83l+HxfjChJRhJ2led1"
			+ "R9nYP9I9KsiDraOUhFhwhA==";

	private static final String PKCS8_PEM =
			"-----BEGIN PRIVATE KEY-----\n"
			+ "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDOg/te0taAGDli\n"
			+ "ozGt93gS1AzYwb+jfr89ojjhj87xWmmgipjA9QTgz5PAK3K+8fDknRItbkICe5wd\n"
			+ "fdREXKuNz7Y0+nzabGfYqaVrUiY0HmzfCCjWoz0u/5TvLGBLh6hHhvG4GjJ9FLQS\n"
			+ "Rff/WH9tklnqqBfGgtmwZJSI5mphDOPx73/K5Ow8VVur1sB9N2qi34oBeZJuv9xt\n"
			+ "+JYsQbtNBmgIxzp0AjPySwX2quTPpsnSrLjMc1TeUhm/kM1Jk0e7CZzIZcKcaKxv\n"
			+ "c1lR+cUr8HX0ZqGfLWN/CPTK0KNVwxxo80Nxg0p9M796xhljjHFUtMIQJSjI5wyR\n"
			+ "wCkRpGllAgMBAAECggEAAL3odir84m6+grGc4P0u/9GmA9T7AVVCzm/J01pET0l4\n"
			+ "mynG9Nx8dRVHIy0qHJr7DKCEJfMhMWfsbo5a5+2gSnezoHBCPg5JIjYGdi1QSKbP\n"
			+ "qSPFnR3EKhTOHhIop2TNmzIre0fmz1xhbYu1oW5vM2ok62BJXj5uleu1PTenVqwR\n"
			+ "nPxo0dqlry1mU0Z+HOhS0hiPJP1ZUbvp7GVsjW07Q5uUO05ZnUFKsuKmFQ1MJoPE\n"
			+ "rKvcLQXFKA9mCH3jC+/7k7TiQjj/WWG+qOsbZcCQAswfZJoCnd58GA3mlYj1nwpw\n"
			+ "zconegYJSZQmT6PQrk8cTXn71iW4RSPrtYhYuqsj8QKBgQDnfo5SCsabjUUufoHU\n"
			+ "2kTJwqyLmm6ocBYinp8Aj8sik0EOTOWgunGI2AkZO2yfnUQW+13IreYQMAeu5ycj\n"
			+ "5c7yPWnzUfxYMm+PU60x8+YwOgFxgfIDvsNpJ4o+2u9+ibeE57Deg1dC3JHly8dv\n"
			+ "z540yiiJQSfIBpaZWTdXBz8hEQKBgQDkYIGPVTkv+tICNt0Pz8hsZ3GIF03/vm6m\n"
			+ "5VrosLmg9zND7dB4LAduGrH3NM3dAebjTCt9nQZSjIU38iUck6w2p83qaVscQAE5\n"
			+ "YrXzr61wfLVEXKGCAabS4T4KAty290tI9ZHZi6eRhBCd1OrrxrkaSHJTq61zZcP4\n"
			+ "729AwtuDFQKBgGtXlrzaPE/iyOKPTLqbX2xC9elh+c/m8YyqCsai5qmoqHDuHUhT\n"
			+ "S9QNgOKPWIYG0YzqkAk/AcAd1WI300FlDXnsGlX2fVGUSYCnZ1cTZniUXRj6DXPJ\n"
			+ "ICmBaSxOLuF5EHzzRmNXeb4KMyjgPiFNn2mRGAzVBpJM48ZFxVB4jsBRAoGAete+\n"
			+ "WQ8Kd9h+5loEhuDJeiYaV43RkuMoOfXc9JU7BG5z0PI21K06QetyFvr8UQkY3OK3\n"
			+ "8hVotTaI4hMKC1kTpUKV7KVHMObswaVrIe05aexqxJ7e8UNaLMyjxlbLyk8y28fb\n"
			+ "8BzCwe870Ooag0CdiJm3hXIvFoeC5oBEY51s3hECgYBWtWhXE2yMtxvoElpW7ghT\n"
			+ "RNJh+1KOXCoa/r4Ct1dMxRtI0KfTTZNXKvKUvTiBRi4qsyrEILc2IWBbkxz6xxI5\n"
			+ "MvgjN/PSXGvd1stYNeLFqAa3aZ23YRUdNMUvdPoh9cyyE83l+HxfjChJRhJ2led1\n"
			+ "R9nYP9I9KsiDraOUhFhwhA==\n"
			+ "-----END PRIVATE KEY-----\n";

	private static final String PKCS1_PEM =
			"-----BEGIN RSA PRIVATE KEY-----\n"
			+ "MIIEogIBAAKCAQEAzoP7XtLWgBg5YqMxrfd4EtQM2MG/o36/PaI44Y/O8VppoIqY\n"
			+ "wPUE4M+TwCtyvvHw5J0SLW5CAnucHX3URFyrjc+2NPp82mxn2Kmla1ImNB5s3wgo\n"
			+ "1qM9Lv+U7yxgS4eoR4bxuBoyfRS0EkX3/1h/bZJZ6qgXxoLZsGSUiOZqYQzj8e9/\n"
			+ "yuTsPFVbq9bAfTdqot+KAXmSbr/cbfiWLEG7TQZoCMc6dAIz8ksF9qrkz6bJ0qy4\n"
			+ "zHNU3lIZv5DNSZNHuwmcyGXCnGisb3NZUfnFK/B19Gahny1jfwj0ytCjVcMcaPND\n"
			+ "cYNKfTO/esYZY4xxVLTCECUoyOcMkcApEaRpZQIDAQABAoIBAAC96HYq/OJuvoKx\n"
			+ "nOD9Lv/RpgPU+wFVQs5vydNaRE9JeJspxvTcfHUVRyMtKhya+wyghCXzITFn7G6O\n"
			+ "WuftoEp3s6BwQj4OSSI2BnYtUEimz6kjxZ0dxCoUzh4SKKdkzZsyK3tH5s9cYW2L\n"
			+ "taFubzNqJOtgSV4+bpXrtT03p1asEZz8aNHapa8tZlNGfhzoUtIYjyT9WVG76exl\n"
			+ "bI1tO0OblDtOWZ1BSrLiphUNTCaDxKyr3C0FxSgPZgh94wvv+5O04kI4/1lhvqjr\n"
			+ "G2XAkALMH2SaAp3efBgN5pWI9Z8KcM3KJ3oGCUmUJk+j0K5PHE15+9YluEUj67WI\n"
			+ "WLqrI/ECgYEA536OUgrGm41FLn6B1NpEycKsi5puqHAWIp6fAI/LIpNBDkzloLpx\n"
			+ "iNgJGTtsn51EFvtdyK3mEDAHrucnI+XO8j1p81H8WDJvj1OtMfPmMDoBcYHyA77D\n"
			+ "aSeKPtrvfom3hOew3oNXQtyR5cvHb8+eNMooiUEnyAaWmVk3Vwc/IRECgYEA5GCB\n"
			+ "j1U5L/rSAjbdD8/IbGdxiBdN/75upuVa6LC5oPczQ+3QeCwHbhqx9zTN3QHm40wr\n"
			+ "fZ0GUoyFN/IlHJOsNqfN6mlbHEABOWK186+tcHy1RFyhggGm0uE+CgLctvdLSPWR\n"
			+ "2YunkYQQndTq68a5GkhyU6utc2XD+O9vQMLbgxUCgYBrV5a82jxP4sjij0y6m19s\n"
			+ "QvXpYfnP5vGMqgrGouapqKhw7h1IU0vUDYDij1iGBtGM6pAJPwHAHdViN9NBZQ15\n"
			+ "7BpV9n1RlEmAp2dXE2Z4lF0Y+g1zySApgWksTi7heRB880ZjV3m+CjMo4D4hTZ9p\n"
			+ "kRgM1QaSTOPGRcVQeI7AUQKBgHrXvlkPCnfYfuZaBIbgyXomGleN0ZLjKDn13PSV\n"
			+ "OwRuc9DyNtStOkHrchb6/FEJGNzit/IVaLU2iOITCgtZE6VCleylRzDm7MGlayHt\n"
			+ "OWnsasSe3vFDWizMo8ZWy8pPMtvH2/AcwsHvO9DqGoNAnYiZt4VyLxaHguaARGOd\n"
			+ "bN4RAoGAVrVoVxNsjLcb6BJaVu4IU0TSYftSjlwqGv6+ArdXTMUbSNCn002TVyry\n"
			+ "lL04gUYuKrMqxCC3NiFgW5Mc+scSOTL4Izfz0lxr3dbLWDXixagGt2mdt2EVHTTF\n"
			+ "L3T6IfXMshPN5fh8X4woSUYSdpXndUfZ2D/SPSrIg62jlIRYcIQ=\n"
			+ "-----END RSA PRIVATE KEY-----\n";

	@Test
	void derBase64InputPassesThroughAsCanonical() {
		assertEquals(DER_BASE64, IntegrationService.normalizeGithubAppPrivateKey(DER_BASE64));
	}

	@Test
	void pkcs8PemInputCollapsesToCanonicalDerBase64() {
		assertEquals(DER_BASE64, IntegrationService.normalizeGithubAppPrivateKey(PKCS8_PEM));
	}

	@Test
	void pkcs1PemInputWrapsAndCollapsesToCanonicalDerBase64() {
		assertEquals(DER_BASE64, IntegrationService.normalizeGithubAppPrivateKey(PKCS1_PEM));
	}

	@Test
	void normalizedOutputIsParseableAsRsaPrivateKey() throws Exception {
		String normalized = IntegrationService.normalizeGithubAppPrivateKey(PKCS1_PEM);
		byte[] der = Base64.getDecoder().decode(normalized);
		PrivateKey pk = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
		assertNotNull(pk);
		assertEquals("RSA", pk.getAlgorithm());
	}
}
