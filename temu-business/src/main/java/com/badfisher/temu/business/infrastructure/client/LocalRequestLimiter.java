package com.badfisher.temu.business.infrastructure.client;

import com.badfisher.temu.client.spi.RequestLimiter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Smooth, per-app limiter for a single instance. It never sends a request without a permit. */
public class LocalRequestLimiter implements RequestLimiter {

	private final Map<String, Long> nextPermit = new ConcurrentHashMap<>();

	private final long intervalNanos;

	private final long maxWaitNanos;

	public LocalRequestLimiter(int requestsPerSecond, int waitSeconds) {
		if (requestsPerSecond < 1 || waitSeconds < 0) {
			throw new IllegalArgumentException("Invalid local rate limit");
		}
		this.intervalNanos = TimeUnit.SECONDS.toNanos(1) / requestsPerSecond;
		this.maxWaitNanos = TimeUnit.SECONDS.toNanos(waitSeconds);
	}

	@Override
	public void acquire(String appId) {
		if (appId == null || appId.trim().isEmpty()) {
			throw new IllegalArgumentException("appId is required for rate limiting");
		}
		long waitNanos;
		synchronized (nextPermit) {
			long now = System.nanoTime();
			long next = nextPermit.getOrDefault(appId, now);
			waitNanos = Math.max(0, next - now);
			if (waitNanos > maxWaitNanos) {
				throw new IllegalStateException("Temu rate limit wait exceeded");
			}
			nextPermit.put(appId, now + waitNanos + intervalNanos);
		}
		try {
			TimeUnit.NANOSECONDS.sleep(waitNanos);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for Temu rate limit", e);
		}
	}

}
