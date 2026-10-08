package com.badfisher.temu.client.util;

import cn.hutool.json.JSONUtil;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Temu 通用工具类 — MD5 签名生成、参数处理等
 *
 * @author lenkor
 */

@Slf4j
@UtilityClass
public final class CommonUtils {

	/**
	 * Temu 开放平台 MD5 签名算法
	 * <p>
	 * 签名规则：appSecret + 按ASCII升序拼接所有参数(key+value) + appSecret → MD5 → 大写
	 * @param params 请求参数（不含sign）
	 * @param appSecret 应用密钥
	 * @return MD5 签名字符串（大写）
	 */
	public static String generateMD5Sign(Map<String, Object> params, String appSecret) {
		try {
			// Step 0: 移除 null 值，避免签名时拼接 "keynull"
			params = removeNullValue(params);

			// Step 1: 按 key ASCII 升序排序
			TreeMap<String, Object> sortedParams = new TreeMap<>(params);

			// Step 2: 拼接 key+value
			String concatenatedParams = sortedParams.entrySet().stream().map(entry -> {
				Object value = entry.getValue();
				if (value instanceof String) {
					return entry.getKey() + value;
				}
				try {
					return entry.getKey() + JSONUtil.toJsonStr(value);
				}
				catch (Exception e) {
					return entry.getKey() + value;
				}
			}).collect(Collectors.joining());

			// Step 3: 首尾拼接 appSecret
			String fullSignString = appSecret + concatenatedParams + appSecret;

			// Step 4: MD5 加密并转大写
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] digest = md.digest(fullSignString.getBytes("UTF-8"));
			StringBuilder hexString = new StringBuilder();
			for (byte b : digest) {
				hexString.append(String.format("%02x", b));
			}
			return hexString.toString().toUpperCase();
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("MD5算法不可用", e);
		}
		catch (Exception e) {
			throw new RuntimeException("MD5签名生成失败", e);
		}
	}

	/**
	 * 移除 Map 中的 null 值
	 */
	public static Map<String, Object> removeNullValue(Map<String, Object> params) {
		if (params == null) {
			return new java.util.HashMap<>();
		}
		return params.entrySet()
			.stream()
			.filter(entry -> entry.getValue() != null)
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

}
