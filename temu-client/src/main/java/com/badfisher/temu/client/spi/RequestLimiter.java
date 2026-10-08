package com.badfisher.temu.client.spi;

/** Acquires permission before sending a request; failures must prevent the HTTP call. */
public interface RequestLimiter {

	void acquire(String appId);

}
