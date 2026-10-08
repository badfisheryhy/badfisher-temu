package com.badfisher.temu.server.api.order;

import com.badfisher.temu.client.TemuApiClient;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.server.web.Result;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemuApiControllerTest {

	@Mock
	private TemuApiClient temuApiClient;

	@Mock
	private TemuAccessTokenService temuAccessTokenService;

	private TemuApiController controller;

	@BeforeEach
	void setUp() {
		controller = new TemuApiController();
		ReflectionTestUtils.setField(controller, "temuApiClient", temuApiClient);
		ReflectionTestUtils.setField(controller, "temuAccessTokenService", temuAccessTokenService);
	}

	@Test
	void shouldRouteGlobalByAuthRegionAndRemoveControlParameters() {
		TemuAccessToken token = token("DEMO-APP-A", "SELLER-1", "GLOBAL", 1);
		when(temuAccessTokenService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.singletonList(token));
		Map<String, Object> apiResponse = Collections.singletonMap("success", true);
		when(temuApiClient.callByAuthRegion(eq(TemuAuthRegion.GLOBAL), any(), same(token))).thenReturn(apiResponse);

		Map<String, Object> request = new LinkedHashMap<>();
		request.put("appId", "DEMO-APP-A");
		request.put("sellerId", "SELLER-1");
		request.put("authRegion", "global");
		request.put("accountType", 1);
		request.put("type", "bg.order.list.v2.get");
		request.put("pageNumber", 1);

		Result<Map<String, Object>> result = controller.callApi(request);

		assertEquals(200, result.getCode());
		assertSame(apiResponse, result.getData());
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
		verify(temuApiClient).callByAuthRegion(eq(TemuAuthRegion.GLOBAL), paramsCaptor.capture(), same(token));
		Map<String, Object> businessParams = paramsCaptor.getValue();
		assertEquals("bg.order.list.v2.get", businessParams.get("type"));
		assertEquals(1, businessParams.get("pageNumber"));
		assertFalse(businessParams.containsKey("appId"));
		assertFalse(businessParams.containsKey("sellerId"));
		assertFalse(businessParams.containsKey("authRegion"));
		assertFalse(businessParams.containsKey("accountType"));
	}

	@Test
	void shouldRejectProductAccountOutsideCnRegion() {
		Map<String, Object> request = new HashMap<>();
		request.put("authRegion", "GLOBAL");
		request.put("accountType", 0);
		request.put("type", "bg.local.goods.list.query");

		Result<Map<String, Object>> result = controller.callApi(request);

		assertEquals(500, result.getCode());
		assertEquals("商品授权(accountType=0)必须使用 authRegion=CN", result.getMessage());
		verify(temuAccessTokenService, never()).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
		verify(temuApiClient, never()).callByAuthRegion(any(), any(), any());
	}

	@Test
	void shouldRouteProductAccountThroughCnRegion() {
		TemuAccessToken token = token("DEMO-APP-A", "SELLER-1", "CN", 0);
		when(temuAccessTokenService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.singletonList(token));
		when(temuApiClient.callByAuthRegion(eq(TemuAuthRegion.CN), any(), same(token)))
			.thenReturn(Collections.singletonMap("success", true));

		Map<String, Object> request = new HashMap<>();
		request.put("appId", "DEMO-APP-A");
		request.put("sellerId", "SELLER-1");
		request.put("authRegion", "CN");
		request.put("accountType", 0);
		request.put("type", "bg.local.goods.list.query");

		Result<Map<String, Object>> result = controller.callApi(request);

		assertEquals(200, result.getCode());
		verify(temuApiClient).callByAuthRegion(eq(TemuAuthRegion.CN), any(), same(token));
	}

	@Test
	void shouldUseStandardStoreControlsAndOnlyForwardBusinessParameters() {
		TemuAccessToken token = token("DEMO-APP-A", "900000000000002", "US", 1);
		token.setSite("US");
		when(temuAccessTokenService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.singletonList(token));
		Map<String, Object> apiResponse = Collections.singletonMap("success", true);
		when(temuApiClient.callByAuthRegion(eq(TemuAuthRegion.US), any(), same(token))).thenReturn(apiResponse);

		Map<String, Object> request = new LinkedHashMap<>();
		request.put("type", "temu.track.trackinginfo.get");
		request.put("sellerId", "900000000000002");
		request.put("site", "US");
		request.put("accountType", 1);
		request.put("parentOrderSn", "DEMO-ORDER-0005");

		Result<Map<String, Object>> result = controller.callApi(request);

		assertEquals(200, result.getCode());
		assertSame(apiResponse, result.getData());
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
		verify(temuApiClient).callByAuthRegion(eq(TemuAuthRegion.US), paramsCaptor.capture(), same(token));
		Map<String, Object> businessParams = paramsCaptor.getValue();
		assertEquals("temu.track.trackinginfo.get", businessParams.get("type"));
		assertEquals("DEMO-ORDER-0005", businessParams.get("parentOrderSn"));
		assertFalse(businessParams.containsKey("sellerId"));
		assertFalse(businessParams.containsKey("site"));
		assertFalse(businessParams.containsKey("accountType"));
	}

	private static TemuAccessToken token(String appId, String sellerId, String authRegion, Integer type) {
		TemuAccessToken token = new TemuAccessToken();
		token.setId(1L);
		token.setAppId(appId);
		token.setSellerId(sellerId);
		token.setAuthRegion(authRegion);
		token.setType(type);
		token.setDelFlag(0);
		return token;
	}

}
