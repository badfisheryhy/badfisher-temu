package com.badfisher.temu.client;

import com.badfisher.temu.client.TemuApiProperties;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.badfisher.temu.client.enums.TemuApiType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemuApiUrlRouterTest {

	@Test
	void shouldResolveRouteByAuthorizationRegion() {
		TemuApiUrlRouter router = routerWithGlobalOrderUrl("https://global.example/router");
		TemuAccessToken account = account(TemuAuthRegion.GLOBAL);

		String url = router.resolveApiUrl(TemuAuthRegion.GLOBAL, orderParams(), account);

		assertThat(url).isEqualTo("https://global.example/router");
	}

	@Test
	void shouldRejectRegionDifferentFromAuthorization() {
		TemuApiUrlRouter router = routerWithGlobalOrderUrl("https://global.example/router");
		TemuAccessToken account = account(TemuAuthRegion.GLOBAL);

		assertThatThrownBy(() -> router.resolveApiUrl(TemuAuthRegion.US, orderParams(), account))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("区域与授权不匹配");
	}

	private static TemuApiUrlRouter routerWithGlobalOrderUrl(String url) {
		TemuApiProperties.Route route = new TemuApiProperties.Route();
		route.setOrderUrl(url);
		Map<String, TemuApiProperties.Route> appRoutes = new HashMap<>();
		appRoutes.put(TemuAuthRegion.GLOBAL.getCode(), route);
		TemuApiProperties properties = new TemuApiProperties();
		properties.setRoutes(Collections.singletonMap("APP-1", appRoutes));

		TemuApiUrlRouter router = new TemuApiUrlRouter(properties);
		return router;
	}

	private static TemuAccessToken account(TemuAuthRegion authRegion) {
		TemuAccessToken account = new TemuAccessToken();
		account.setAppId("APP-1");
		account.setSellerId("SELLER-1");
		account.setAuthRegion(authRegion.getCode());
		account.setType(TemuApiType.AccountType.ORDER.getCode());
		return account;
	}

	private static Map<String, Object> orderParams() {
		Map<String, Object> params = new HashMap<>();
		params.put("type", TemuApiType.ORDER_LIST_V2_GET.getType());
		return params;
	}

}
