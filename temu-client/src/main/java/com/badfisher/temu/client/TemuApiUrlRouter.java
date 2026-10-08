package com.badfisher.temu.client;

import com.badfisher.temu.client.TemuApiProperties;
import com.badfisher.temu.client.auth.TemuCredentials;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.badfisher.temu.client.enums.TemuApiType;

import java.util.Map;

/** 根据主体 appId 和 API 端点选择专属出口代理。 */
public class TemuApiUrlRouter {

	private final TemuApiProperties properties;

	public TemuApiUrlRouter(TemuApiProperties properties) {
		this.properties = java.util.Objects.requireNonNull(properties, "properties");
	}

	public String resolveApiUrl(TemuAuthRegion authRegion, Map<String, Object> params, TemuCredentials account) {
		String appId = account.getAppId();
		if (appId == null || appId.trim().isEmpty()) {
			throw new IllegalArgumentException("Temu 授权记录缺少 appId, sellerId=" + account.getSellerId());
		}

		Map<String, TemuApiProperties.Route> regionRoutes = properties.getRoutes().get(appId.trim());
		if (regionRoutes == null) {
			throw new IllegalStateException("未配置 Temu API 主体路由, appId=" + appId);
		}

		TemuAuthRegion accountRegion = TemuAuthRegion.fromCode(account.getAuthRegion());
		if (accountRegion == null) {
			throw new IllegalStateException(
					"授权记录缺少有效authRegion, sellerId=" + account.getSellerId() + ", site=" + account.getSite());
		}
		if (authRegion == null || authRegion != accountRegion) {
			throw new IllegalArgumentException(
					"Temu API调用区域与授权不匹配: requested=" + authRegion + ", actual=" + accountRegion.getCode());
		}
		TemuApiProperties.Route route = regionRoutes.get(authRegion.getCode());
		if (route == null) {
			throw new IllegalStateException(
					"主体未配置 Temu 授权区域路由, appId=" + appId + ", authRegion=" + authRegion.getCode());
		}

		String type = params.get("type") == null ? null : String.valueOf(params.get("type"));
		TemuApiType apiType = TemuApiType.fromType(type);
		if (apiType == null) {
			throw new IllegalArgumentException("未维护的 Temu API type: " + type);
		}
		if (account.getType() == null || account.getType() != apiType.getAccountType().getCode()) {
			throw new IllegalArgumentException("Temu API 与授权类型不匹配: type=" + type + ", requiredAccountType="
					+ apiType.getAccountType().getCode() + ", actualAccountType=" + account.getType());
		}

		String apiUrl = apiType.getAccountType() == TemuApiType.AccountType.PRODUCT ? route.getPartnerUrl()
				: route.getOrderUrl();
		if (apiUrl == null || apiUrl.trim().isEmpty()) {
			throw new IllegalStateException("主体未配置 Temu " + apiType.getAccountType() + " 代理 URL, appId=" + appId
					+ ", authRegion=" + authRegion.getCode());
		}
		return apiUrl.trim();
	}

}
