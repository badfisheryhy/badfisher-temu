package com.badfisher.temu.client;

import com.badfisher.temu.client.auth.TemuCredentials;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.badfisher.temu.client.exception.TemuApiException;
import com.badfisher.temu.client.spi.ApiCallLogger;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TemuApiClientAuditTest {

	private final AtomicInteger auditCalls = new AtomicInteger();

	private final TemuCredentials account = account();

	@Test
	void auditFailureShouldPreserveSuccessfulResponse() {
		try (TemuApiClient client = client(response(200, "{\"success\":true,\"result\":{\"value\":42}}"))) {
			Map<String, Object> result = call(client);
			assertEquals(Boolean.TRUE, result.get("success"));
			assertEquals(42, ((Map<?, ?>) result.get("result")).get("value"));
			assertEquals(1, auditCalls.get());
		}
	}

	@Test
	void auditFailureShouldPreserveBothPlatformErrorFormats() {
		String[] bodies = {
				"{\"error_response\":{\"code\":\"21008\",\"msg\":\"platform failure\"}}",
				"{\"success\":false,\"errorCode\":\"21008\",\"errorMsg\":\"platform failure\"}" };
		for (String body : bodies) {
			try (TemuApiClient client = client(response(200, body))) {
				TemuApiException error = assertThrows(TemuApiException.class, () -> call(client));
				assertEquals("21008", error.getErrorCode());
				assertTrue(error.getMessage().contains("platform failure"));
			}
		}
		assertEquals(2, auditCalls.get());
	}

	@Test
	void auditFailureShouldPreserveHttpFailure() {
		try (TemuApiClient client = client(response(503, "{}"))) {
			TemuApiException error = assertThrows(TemuApiException.class, () -> call(client));
			assertTrue(error.getMessage().contains("HTTP 503"));
			assertEquals(1, auditCalls.get());
		}
	}

	@Test
	void auditFailureShouldPreserveOriginalIoCause() {
		IOException transportFailure = new IOException("simulated transport failure");
		try (TemuApiClient client = client(chain -> {
			throw transportFailure;
		})) {
			TemuApiException error = assertThrows(TemuApiException.class, () -> call(client));
			assertSame(transportFailure, error.getCause());
			assertEquals(1, auditCalls.get());
		}
	}

	private Map<String, Object> call(TemuApiClient client) {
		return client.callByAuthRegion(TemuAuthRegion.US,
				Collections.<String, Object>singletonMap("type", "bg.order.list.v2.get"), account);
	}

	private TemuApiClient client(Interceptor interceptor) {
		TemuApiUrlRouter router = mock(TemuApiUrlRouter.class);
		when(router.resolveApiUrl(any(), any(), any())).thenReturn("https://example.invalid/api");
		ApiCallLogger logger = (appId, apiName, apiUrl, method, status, code, message, request, response, cost,
				success, sellerId) -> {
			auditCalls.incrementAndGet();
			throw new IllegalStateException("simulated audit failure");
		};
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(interceptor).build();
		return new TemuApiClient(router, appId -> { }, logger, http);
	}

	private static Interceptor response(int status, String body) {
		return chain -> new Response.Builder()
			.request(chain.request())
			.protocol(Protocol.HTTP_1_1)
			.code(status)
			.message("test response")
			.body(ResponseBody.create(MediaType.parse("application/json"), body))
			.build();
	}

	private static TemuCredentials account() {
		TemuCredentials account = mock(TemuCredentials.class);
		when(account.getAppId()).thenReturn("demo-app");
		when(account.getAppKey()).thenReturn("demo-key");
		when(account.getAppSecret()).thenReturn("demo-secret");
		when(account.getAccessToken()).thenReturn("demo-token");
		when(account.getSellerId()).thenReturn("demo-seller");
		return account;
	}

}
