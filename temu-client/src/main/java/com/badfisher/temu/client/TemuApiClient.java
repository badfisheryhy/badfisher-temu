package com.badfisher.temu.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.badfisher.temu.client.auth.TemuCredentials;
import com.badfisher.temu.client.spi.ApiCallLogger;
import com.badfisher.temu.client.spi.RequestLimiter;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.badfisher.temu.client.enums.TemuApiErrorCode;
import com.badfisher.temu.client.exception.TemuApiException;
import com.badfisher.temu.client.util.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Temu 开放平台 API 客户端 — 封装 Temu 开放平台的 HTTP 调用与 MD5 签名
 * <p>
 * Temu API 采用单一入口路由模式：所有请求 POST 到 /openapi/router，通过 type 参数区分接口。 签名规则：appSecret +
 * 按ASCII升序拼接 key+value + appSecret → MD5 大写。
 *
 * @author lenkor
 */
@Slf4j
public class TemuApiClient implements AutoCloseable {

	private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final RequestLimiter requestLimiter;

	private final TemuApiUrlRouter temuApiUrlRouter;

	private final ApiCallLogger temuApiLogService;

	private final OkHttpClient httpClient;

	/** All infrastructure dependencies are provided explicitly by the application. */
	public TemuApiClient(TemuApiUrlRouter router, RequestLimiter limiter, ApiCallLogger logger,
			OkHttpClient httpClient) {
		this.temuApiUrlRouter = java.util.Objects.requireNonNull(router, "router");
		this.requestLimiter = java.util.Objects.requireNonNull(limiter, "limiter");
		this.temuApiLogService = java.util.Objects.requireNonNull(logger, "logger");
		this.httpClient = java.util.Objects.requireNonNull(httpClient, "httpClient");
	}

	/** 根据授权/API路由区域调用 Temu API。 */
	public Map<String, Object> callByAuthRegion(TemuAuthRegion authRegion, Map<String, Object> params,
			TemuCredentials account) {
		if (authRegion == null) {
			throw new IllegalArgumentException("Temu授权区域不能为空");
		}
		String apiUrl = temuApiUrlRouter.resolveApiUrl(authRegion, params, account);
		requestLimiter.acquire(account.getAppId());
		return callApi(apiUrl, new LinkedHashMap<>(params), account);
	}

	/**
	 * 通用 API 调用
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> callApi(String apiUrl, Map<String, Object> params, TemuCredentials account) {
		String appKey = account.getAppKey();
		String appId = account.getAppId();
		String appSecret = account.getAppSecret();
		String accessToken = account.getAccessToken();
		String sellerId = account.getSellerId();

		// 1. 添加公共参数
		params.put("app_key", appKey);
		params.put("access_token", accessToken);
		params.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
		params.put("data_type", "JSON");

		// 2. 移除旧的签名（如果有），重新生成
		params.remove("sign");
		String sign = CommonUtils.generateMD5Sign(params, appSecret);
		params.put("sign", sign);

		String apiName = String.valueOf(params.get("type"));

		String requestJson;
		try {
			requestJson = OBJECT_MAPPER.writeValueAsString(params);
		}
		catch (Exception e) {
			throw new RuntimeException("序列化请求参数失败", e);
		}
		String safeRequestJson = safeRequestJson(params);

		log.debug("Temu API 请求: url={}, body={}", apiUrl, safeRequestJson);

		RequestBody requestBody = RequestBody.create(JSON_MEDIA_TYPE, requestJson);
		Request request = new Request.Builder().url(apiUrl).post(requestBody).build();

		// 3. 执行请求
		long start = System.currentTimeMillis();
		try (Response response = httpClient.newCall(request).execute()) {
			long costMs = System.currentTimeMillis() - start;
			String responseBody = response.body() != null ? response.body().string() : null;
			String safeResponseBody = LogSanitizer.sanitize(responseBody);

			if (response.isSuccessful()) {
				log.debug("Temu API 响应: costMs={}, body={}", costMs, safeResponseBody);

				Map<String, Object> resultMap = OBJECT_MAPPER.readValue(responseBody,
						new TypeReference<Map<String, Object>>() {
						});

				// 业务层错误（非 HTTP 错误）
				if (resultMap.containsKey("error_response")) {
					Map<String, Object> errorResp = (Map<String, Object>) resultMap.get("error_response");
					String errorMsg = String.valueOf(errorResp.get("msg"));
					String errorCode = String.valueOf(errorResp.get("code"));
					TemuApiErrorCode errorEnum = TemuApiErrorCode.fromObject(errorResp.get("code"));
					log.error("Temu API 业务错误: url={}, code={}, reason={}, msg={}, request={}, response={}", apiUrl,
							errorCode, errorEnum != null ? errorEnum.getChnDesc() : "未知", errorMsg, safeRequestJson,
							safeResponseBody);
					saveAuditSafely(appId, apiName, apiUrl, "POST", response.code(), errorCode, errorMsg,
							safeRequestJson, safeResponseBody, costMs, 0, sellerId);
					throw new TemuApiException(errorEnum, errorCode, errorMsg, apiUrl, safeRequestJson, safeResponseBody);
				}

				// 业务层错误（success=false 格式，如地址接口返回 "The current order status cannot be
				// viewed."）
				if (Boolean.FALSE.equals(resultMap.get("success"))) {
					String errorCode = resultMap.get("errorCode") != null ? String.valueOf(resultMap.get("errorCode"))
							: null;
					String errorMsg = resultMap.get("errorMsg") != null ? String.valueOf(resultMap.get("errorMsg"))
							: "未知错误";
					TemuApiErrorCode errorEnum = TemuApiErrorCode.fromObject(resultMap.get("errorCode"));
					log.error("Temu API 业务错误: url={}, code={}, reason={}, msg={}, request={}, response={}", apiUrl,
							errorCode, errorEnum != null ? errorEnum.getChnDesc() : "未知", errorMsg, safeRequestJson,
							safeResponseBody);
					saveAuditSafely(appId, apiName, apiUrl, "POST", response.code(), errorCode, errorMsg,
							safeRequestJson, safeResponseBody, costMs, 0, sellerId);
					throw new TemuApiException(errorEnum, errorCode, errorMsg, apiUrl, safeRequestJson, safeResponseBody);
				}

				saveAuditSafely(appId, apiName, apiUrl, "POST", response.code(), null, null, safeRequestJson,
						safeResponseBody, costMs, 1, sellerId);
				return resultMap;
			}
			else {
				String errorMsg = "HTTP " + response.code() + " " + response.message();
				log.error("Temu API 请求失败: url={}, {}, request={}, response={}", apiUrl, errorMsg, safeRequestJson,
						safeResponseBody);
				saveAuditSafely(appId, apiName, apiUrl, "POST", response.code(), null, errorMsg, safeRequestJson,
						safeResponseBody, costMs, 0, sellerId);
				throw new TemuApiException("Temu API 请求失败: " + errorMsg, apiUrl, safeRequestJson, safeResponseBody);
			}

		}
		catch (IOException e) {
			long costMs = System.currentTimeMillis() - start;
			log.error("Temu API 调用异常: url={}, error={}, request={}", apiUrl, e.getMessage(), safeRequestJson, e);
			saveAuditSafely(appId, apiName, apiUrl, "POST", null, null, e.getMessage(), safeRequestJson, null,
					costMs, 0, sellerId);
			throw new TemuApiException("Temu API 调用失败: " + e.getMessage(), apiUrl, safeRequestJson, e);
		}
	}

	/** 审计回调失败只记录概要，不覆盖平台响应或原始调用异常。 */
	private void saveAuditSafely(String appId, String apiName, String apiUrl, String httpMethod, Integer statusCode,
			String errorCode, String errorMessage, String requestBody, String responseBody, long costMs, int success,
			String sellerId) {
		try {
			temuApiLogService.save(appId, apiName, apiUrl, httpMethod, statusCode, errorCode, errorMessage,
					requestBody, responseBody, costMs, success, sellerId);
		}
		catch (RuntimeException e) {
			// 外部实现的异常消息可能包含凭证，仅记录异常类型和接口名称。
			log.error("Temu API 审计回调失败: apiName={}, exceptionType={}", apiName, e.getClass().getName());
		}
	}

	/** Log only sanitized payloads; HTTP requests retain the signed values. */
	private static String safeRequestJson(Map<String, Object> params) {
		try {
			return LogSanitizer.sanitize(OBJECT_MAPPER.writeValueAsString(params));
		}
		catch (Exception e) {
			return "{\"masked\":true}";
		}
	}

	@Override
	public void close() {
		httpClient.dispatcher().executorService().shutdown();
		httpClient.connectionPool().evictAll();
	}

}
