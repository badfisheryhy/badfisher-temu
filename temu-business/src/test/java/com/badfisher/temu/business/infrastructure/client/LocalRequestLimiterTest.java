package com.badfisher.temu.business.infrastructure.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalRequestLimiterTest {

	@Test
	void rejectsExhaustedAppWithoutBlockingAnotherApp() {
		LocalRequestLimiter limiter = new LocalRequestLimiter(1, 0);
		limiter.acquire("app-a");
		assertThatThrownBy(() -> limiter.acquire("app-a")).isInstanceOf(IllegalStateException.class);
		limiter.acquire("app-b");
	}

	@Test
	void rejectsInvalidConfigurationAndMissingIdentity() {
		assertThatThrownBy(() -> new LocalRequestLimiter(0, 1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new LocalRequestLimiter(1, 0).acquire(" "))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
