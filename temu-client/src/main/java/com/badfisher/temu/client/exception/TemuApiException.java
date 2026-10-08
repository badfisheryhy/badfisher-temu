package com.badfisher.temu.client.exception;

import com.badfisher.temu.client.enums.TemuApiErrorCode;
import lombok.Getter;

/**
 * Temu API 拉取异常 — 调用 Temu 开放平台 API 失败时抛出。
 * <p>
 * 携带 {@link TemuApiErrorCode} 错误码枚举，便于上层按错误码分类处理：
 * <ul>
 * <li>access_token 过期(3000034) → 需提醒卖家重新授权</li>
 * <li>限流(4000004) → 可等待重试</li>
 * <li>系统异常(4000000) → 可重试</li>
 * <li>参数错误(3000000) → 不可重试，需修正参数</li>
 * </ul>
 *
 * @author lenkor
 */
@Getter
public class TemuApiException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** Temu API 错误码枚举（可能为 null，如 HTTP 错误、网络异常等非业务错误） */
	private final TemuApiErrorCode errorCodeEnum;

	/** 错误码（原始值，可能为 null） */
	private final String errorCode;

	/** 请求地址 */
	private final String apiUrl;

	/** 请求报文 */
	private final String requestBody;

	/** 响应报文 */
	private final String responseBody;

	/**
	 * 业务错误 — 携带错误码枚举
	 * @param errorEnum 错误码枚举
	 * @param errorCode 错误码字符串
	 * @param errorMsg 错误消息
	 * @param apiUrl 请求地址
	 * @param requestBody 请求报文
	 * @param responseBody 响应报文
	 */
	public TemuApiException(TemuApiErrorCode errorEnum, String errorCode, String errorMsg, String apiUrl,
			String requestBody, String responseBody) {
		super(buildMessage(errorEnum, errorCode, errorMsg));
		this.errorCodeEnum = errorEnum;
		this.errorCode = errorCode;
		this.apiUrl = apiUrl;
		this.requestBody = requestBody;
		this.responseBody = responseBody;
	}

	/**
	 * HTTP 错误 / 网络异常 — 无错误码枚举
	 * @param errorMsg 错误消息
	 * @param apiUrl 请求地址
	 * @param requestBody 请求报文
	 * @param responseBody 响应报文
	 */
	public TemuApiException(String errorMsg, String apiUrl, String requestBody, String responseBody) {
		super(errorMsg);
		this.errorCodeEnum = null;
		this.errorCode = null;
		this.apiUrl = apiUrl;
		this.requestBody = requestBody;
		this.responseBody = responseBody;
	}

	/**
	 * 网络异常 — 包装 IOException
	 * @param errorMsg 错误消息
	 * @param apiUrl 请求地址
	 * @param requestBody 请求报文
	 * @param cause 原始异常
	 */
	public TemuApiException(String errorMsg, String apiUrl, String requestBody, Throwable cause) {
		super(errorMsg, cause);
		this.errorCodeEnum = null;
		this.errorCode = null;
		this.apiUrl = apiUrl;
		this.requestBody = requestBody;
		this.responseBody = null;
	}

	private static String buildMessage(TemuApiErrorCode errorEnum, String errorCode, String errorMsg) {
		if (errorEnum != null) {
			return "Temu API 业务错误: [" + errorCode + "] " + errorEnum.getChnDesc() + " — " + errorMsg;
		}
		return "Temu API 业务错误: [" + errorCode + "] " + errorMsg;
	}

	/**
	 * 是否可重试 — 限流、系统异常等暂时性错误
	 */
	public boolean isRetryable() {
		if (errorCodeEnum == null) {
			// 无业务错误码表示 HTTP/网络异常；未知业务错误仍携带原始 errorCode。
			return errorCode == null;
		}
		switch (errorCodeEnum) {
			case RATE_LIMIT_EXCEEDED:
			case SYSTEM_EXCEPTION:
			case BUSINESS_EXCEPTION:
			case BUSINESS_SERVICE_ERROR:
				return true;
			default:
				return false;
		}
	}

	/**
	 * 是否 token 过期 — 需提醒卖家重新授权
	 */
	public boolean isTokenExpired() {
		return errorCodeEnum == TemuApiErrorCode.ACCESS_TOKEN_EXPIRED;
	}

}
