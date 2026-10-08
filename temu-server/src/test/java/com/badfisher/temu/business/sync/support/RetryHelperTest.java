package com.badfisher.temu.business.sync.support;

import com.badfisher.temu.client.enums.TemuApiErrorCode;
import com.badfisher.temu.business.sync.execution.TaskLeaseLostException;
import com.badfisher.temu.client.exception.TemuApiException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryHelperTest {

	@Test
	void shouldRetryTransientApiErrorOnce() {
		AtomicInteger attempts = new AtomicInteger();

		String result = RetryHelper.withRetry("限流测试", () -> {
			if (attempts.incrementAndGet() == 1) {
				throw businessError(TemuApiErrorCode.RATE_LIMIT_EXCEEDED);
			}
			return "ok";
		});

		assertEquals("ok", result);
		assertEquals(2, attempts.get());
	}

	@Test
	void shouldNotRetryNonRetryableBusinessError() {
		AtomicInteger attempts = new AtomicInteger();

		assertThrows(TemuApiException.class, () -> RetryHelper.withRetry("权限测试", () -> {
			attempts.incrementAndGet();
			throw businessError(TemuApiErrorCode.ACCESS_TOKEN_NO_PERMISSION);
		}));

		assertEquals(1, attempts.get());
	}

	@Test
	void shouldRetryTransportErrorOnce() {
		AtomicInteger attempts = new AtomicInteger();

		String result = RetryHelper.withRetry("网络测试", () -> {
			if (attempts.incrementAndGet() == 1) {
				throw new TemuApiException("HTTP 503", "url", "request", "response");
			}
			return "ok";
		});

		assertEquals("ok", result);
		assertEquals(2, attempts.get());
	}

	@Test
	void shouldNotRetryAfterLosingExecutionLease() {
		AtomicInteger attempts = new AtomicInteger();
		assertThrows(TaskLeaseLostException.class, () -> RetryHelper.withRetry("失租测试", () -> {
			attempts.incrementAndGet();
			throw new TaskLeaseLostException("lease lost");
		}));
		assertEquals(1, attempts.get());
	}

	private TemuApiException businessError(TemuApiErrorCode errorCode) {
		return new TemuApiException(errorCode, String.valueOf(errorCode.getCode()), "test", "url", "request",
				"response");
	}

}
