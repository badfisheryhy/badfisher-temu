package com.badfisher.temu.business.infrastructure.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime settings for HTTP transport and rate limiting. */
@Data
@ConfigurationProperties(prefix = "temu.client")
public class ClientRuntimeProperties {

	private int connectTimeoutSeconds = 30;

	private int readTimeoutSeconds = 60;

	private int writeTimeoutSeconds = 30;

	private int requestsPerSecond = 18;

	private int rateLimitWaitSeconds = 1;

	/** local is for a single application instance; redis coordinates multiple instances. */
	private String rateLimitMode = "local";

	private String redisAddress = "redis://localhost:6379";

	@lombok.ToString.Exclude
	private String redisPassword;

	public void validate() {
		if (connectTimeoutSeconds < 1 || readTimeoutSeconds < 1 || writeTimeoutSeconds < 1
				|| requestsPerSecond < 1 || requestsPerSecond > 10000 || rateLimitWaitSeconds < 0
				|| rateLimitWaitSeconds > 60) {
			throw new IllegalArgumentException("Invalid temu.client timeout or rate-limit settings");
		}
		if (!"local".equals(rateLimitMode) && !"redis".equals(rateLimitMode)) {
			throw new IllegalArgumentException("temu.client.rate-limit-mode must be local or redis");
		}
	}

}
