package com.badfisher.temu.client;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/** Temu API 主体路由配置，按 appId 隔离不同主体的出口 IP。 */
@Data
public class TemuApiProperties {

	/** 第一层 key 为 appId，第二层 key 为授权区域（US/GLOBAL/EU/CN）。 */
	private Map<String, Map<String, Route>> routes = new HashMap<>();

	@Data
	public static class Route {

		/** 订单、售后、物流等订单授权接口代理 URL。 */
		private String orderUrl;

		/** 商品、库存等 Partner 端接口代理 URL。 */
		private String partnerUrl;

	}

}
