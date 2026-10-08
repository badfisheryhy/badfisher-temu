package com.badfisher.temu.business.infrastructure.client;

import com.badfisher.temu.client.TemuApiClient;
import com.badfisher.temu.client.TemuApiProperties;
import com.badfisher.temu.client.TemuApiUrlRouter;
import com.badfisher.temu.client.spi.ApiCallLogger;
import com.badfisher.temu.client.spi.RequestLimiter;
import okhttp3.OkHttpClient;
import org.redisson.Redisson;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/** Assembles the standalone client with the selected rate limiter and persistent audit sink. */
@Configuration
@EnableConfigurationProperties(ClientRuntimeProperties.class)
public class TemuClientConfiguration {

	@Bean
	@ConfigurationProperties(prefix = "temu.api")
	public TemuApiProperties temuApiProperties() {
		return new TemuApiProperties();
	}

	@Bean
	public TemuApiUrlRouter temuApiUrlRouter(TemuApiProperties properties) {
		return new TemuApiUrlRouter(properties);
	}

	@Bean
	@ConditionalOnProperty(prefix = "temu.client", name = "rate-limit-mode", havingValue = "local", matchIfMissing = true)
	public RequestLimiter localRequestLimiter(ClientRuntimeProperties properties) {
		properties.validate();
		return new LocalRequestLimiter(properties.getRequestsPerSecond(), properties.getRateLimitWaitSeconds());
	}

	@Bean(destroyMethod = "shutdown")
	@ConditionalOnProperty(prefix = "temu.client", name = "rate-limit-mode", havingValue = "redis")
	public RedissonClient redissonClient(ClientRuntimeProperties properties) {
		Config config = new Config();
		SingleServerConfig server = config.useSingleServer().setAddress(properties.getRedisAddress());
		if (properties.getRedisPassword() != null && !properties.getRedisPassword().isEmpty()) {
			server.setPassword(properties.getRedisPassword());
		}
		return Redisson.create(config);
	}

	@Bean
	@ConditionalOnProperty(prefix = "temu.client", name = "rate-limit-mode", havingValue = "redis")
	public RequestLimiter redisRequestLimiter(RedissonClient redisson, ClientRuntimeProperties properties) {
		properties.validate();
		return appId -> {
			if (appId == null || appId.trim().isEmpty()) {
				throw new IllegalArgumentException("appId is required for rate limiting");
			}
			RRateLimiter limiter = redisson.getRateLimiter("badfisher:temu:rate:" + appId.trim());
			limiter.trySetRate(RateType.OVERALL, properties.getRequestsPerSecond(), 1, RateIntervalUnit.SECONDS);
			if (!limiter.tryAcquire(1, properties.getRateLimitWaitSeconds(), TimeUnit.SECONDS)) {
				throw new IllegalStateException("Temu rate limit wait exceeded");
			}
		};
	}

	@Bean(destroyMethod = "close")
	public TemuApiClient temuApiClient(TemuApiUrlRouter router, RequestLimiter limiter, ApiCallLogger logger,
			ClientRuntimeProperties properties) {
		properties.validate();
		OkHttpClient http = new OkHttpClient.Builder()
			.connectTimeout(properties.getConnectTimeoutSeconds(), TimeUnit.SECONDS)
			.readTimeout(properties.getReadTimeoutSeconds(), TimeUnit.SECONDS)
			.writeTimeout(properties.getWriteTimeoutSeconds(), TimeUnit.SECONDS)
			.build();
		return new TemuApiClient(router, limiter, logger, http);
	}

}
