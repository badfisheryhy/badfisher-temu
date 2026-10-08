package com.badfisher.temu.client.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Temu 开放平台 API 错误码枚举
 *
 * @author lenkor
 */
@Getter
public enum TemuApiErrorCode {

	/** 成功 */
	SUCCESS(1000000, "SUCCESS", "成功"),

	/** 内部服务异常 — 调用内部 API 失败 */
	BUSINESS_EXCEPTION(2000000, "BUSINESS_EXCEPTION", "内部服务异常"),

	// ==================== 请求参数/签名错误 (3000000~) ====================

	/** 参数错误 */
	BAD_PARAMS(3000000, "BAD_PARAMS", "请求参数错误"),
	/** 签名无效 */
	SIGN_INVALID(3000001, "SIGN_UNVALID", "签名无效"),
	/** 缺少 type 参数 */
	TYPE_MISSING(3000002, "there is no type in body.", "缺少 API type 参数"),
	/** type 不存在 */
	TYPE_NOT_EXISTS(3000003, "type not exists", "API type 不存在"),
	/** type 已废弃 */
	TYPE_SUNSET(3000004, "type has been sunset, please stop calling this type and change to use another one.",
			"API type 已废弃，请更换"),
	/** 缺少 timestamp 参数 */
	TIMESTAMP_MISSING(3000010, "there is no timestamp in body.", "缺少 timestamp 参数"),
	/** timestamp 无效 — 超过当前时间 + 300s */
	TIMESTAMP_INVALID(3000011, "timestamp is invalid.", "timestamp 无效"),
	/** timestamp 已过期 */
	TIMESTAMP_EXPIRED(3000012, "timestamp is expired.", "timestamp 已过期"),
	/** 缺少 data_type 参数 */
	DATA_TYPE_MISSING(3000013, "there is no data_type in body.", "缺少 data_type 参数"),
	/** data_type 无效 — 仅支持 Json */
	DATA_TYPE_INVALID(3000014, "data_type is invalid", "data_type 无效（仅支持 Json）"),
	/** 缺少 client_id */
	CLIENT_ID_MISSING(3000019, "there is no client_id in body.", "缺少 app_key(client_id)"),
	/** client_id 不存在 */
	CLIENT_ID_NOT_EXISTS(3000020, "client_id not exists.", "app_key(client_id) 不存在"),
	/** client_id 无此 API 权限 */
	CLIENT_ID_NO_PERMISSION(3000021, "client_id don't have this api permission.", "app_key 无此 API 权限"),
	/** client_id 已被停用 */
	CLIENT_ID_SUSPENDED(3000022,
			"client_id have been suspended, please contact temu product support team to recover your app status.",
			"app_key 已被停用，请联系 Temu 恢复"),
	/** 缺少 app_key */
	APP_KEY_MISSING(3000025, "there is no app_key in body.", "缺少 app_key 参数"),
	/** app_key 不存在 */
	APP_KEY_NOT_EXISTS(3000026, "app_key not exists.", "app_key 不存在"),
	/** app_key 无此 API 权限 */
	APP_KEY_NO_PERMISSION(3000027, "app_key don't have this api permission.", "app_key 无此 API 权限"),
	/** app_key 已被停用 */
	APP_KEY_SUSPENDED(3000028,
			"app_key have been suspended, please contact temu product support team to recover your app status.",
			"app_key 已被停用，请联系 Temu 恢复"),
	/** 缺少 access_token */
	ACCESS_TOKEN_MISSING(3000030, "there is no access_token in body.", "缺少 access_token 参数"),
	/** access_token 不存在 */
	ACCESS_TOKEN_NOT_EXISTS(3000031, "access_token not exists.", "access_token 不存在"),
	/** access_token 无此 API 权限 — 需卖家在商家中心授权 */
	ACCESS_TOKEN_NO_PERMISSION(3000032,
			"access_token don't have this api access, please ask for seller to authorize this api in seller center first.",
			"access_token 无此 API 权限，需卖家授权"),
	/** access_token 与 app_key 不匹配 */
	ACCESS_TOKEN_NOT_MATCH(3000033, "access_token and app_key are not mapping.", "access_token 与 app_key 不匹配"),
	/** access_token 已过期或被刷新 */
	ACCESS_TOKEN_EXPIRED(3000034,
			"access_token is expired or have been refreshed, please contact seller to share the new access_token with you.",
			"access_token 已过期，需卖家重新分享"),
	/** 缺少 sign 参数 */
	SIGN_MISSING(3000040, "there is no sign in body.", "缺少 sign 参数"),

	// ==================== 系统/限流错误 (4000000~) ====================

	/** 系统异常 */
	SYSTEM_EXCEPTION(4000000, "SYSTEM_EXCEPTION", "系统异常"),
	/** 请求限流 — 超过速率限制 */
	RATE_LIMIT_EXCEEDED(4000004, "RATE_LIMIT_EXCEED_EXCEPTION", "请求频率超限"),

	// ==================== 鉴权/路由错误 (5000000~) ====================

	/** 商家未授权 / 令牌验证失败 */
	UNAUTHORIZED(5000000, "AUTHORIZE_NOT_ALLOW", "商家未授权或令牌验证失败"),
	/** 文件上传路由必须包含 upload */
	ROUTE_INVALID(5000001, "ROUTER_NOT_ALLOW", "文件上传路由需包含 upload"),
	/** 非正式环境 */
	NOT_PRODUCTION_ENV(5000002, "NOT_SUPPORT_STAGING_ENV", "非正式环境，请检查请求地址"),
	/** IP 不在白名单 */
	IP_NOT_WHITELISTED(5000003, "NOT_IN_IP_WHITE_LIST", "请求 IP 不在白名单"),

	// ==================== 内部错误 ====================

	/** 内部 API 不存在 */
	RPC_INTERFACE_NOT_FOUND(6000001, "RPC_INTERFACE_NOT_FOUND", "内部 API 不存在"),

	/** 业务服务错误 */
	BUSINESS_SERVICE_ERROR(7000000, "BUSINESS_SERVICE_ERROR", "业务服务错误"),

	// ==================== 售后模块错误 (130010000~) ====================

	/** 售后系统错误 */
	AFTERSALES_SYSTEM_ERROR(130010000, "系统错误", "售后系统错误"),
	/** 售后参数不合法 */
	AFTERSALES_BAD_PARAMS(130010001, "该参数不合法。请检查输入参数是否符合规定。", "售后参数不合法"),
	/** 订单已全部发货 */
	AFTERSALES_ORDER_ALL_SHIPPED(130010002, "订单已全部发货。", "订单已全部发货"),
	/** 操作禁止 */
	AFTERSALES_OPERATION_FORBIDDEN(130010005, "操作禁止", "售后操作禁止"),;

	private final int code;

	private final String engMsg;

	private final String engDesc;

	private final String chnDesc;

	private static final Map<Integer, TemuApiErrorCode> CODE_MAP = new HashMap<>();

	static {
		for (TemuApiErrorCode e : values()) {
			CODE_MAP.put(e.code, e);
		}
	}

	TemuApiErrorCode(int code, String engMsg, String chnDesc) {
		this.code = code;
		this.engMsg = engMsg;
		this.engDesc = name();
		this.chnDesc = chnDesc;
	}

	/**
	 * 根据错误码获取枚举
	 */
	public static TemuApiErrorCode fromCode(int code) {
		return CODE_MAP.get(code);
	}

	/**
	 * 根据 error_response 中的 code 解析
	 */
	public static TemuApiErrorCode fromObject(Object codeObj) {
		if (codeObj == null)
			return null;
		try {
			return fromCode(Integer.parseInt(String.valueOf(codeObj)));
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

}
