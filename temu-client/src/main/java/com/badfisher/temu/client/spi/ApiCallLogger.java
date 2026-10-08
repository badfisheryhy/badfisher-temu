package com.badfisher.temu.client.spi;

/** Receives sanitized call details. Implementations must not expose credentials. */
public interface ApiCallLogger {

	void save(String appId, String apiName, String apiUrl, String httpMethod, Integer statusCode,
			String errorCode, String errorMessage, String requestBody, String responseBody, long costMs, int success,
			String sellerId);

}
