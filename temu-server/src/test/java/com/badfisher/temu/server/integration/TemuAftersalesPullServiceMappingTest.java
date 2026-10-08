package com.badfisher.temu.server.integration;

import com.badfisher.temu.business.aftersales.service.TemuAftersalesPullService;

import com.badfisher.temu.business.aftersales.model.TemuAftersalesReturnLogistics;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.aftersales.model.TemuAftersalesProduct;
import com.badfisher.temu.business.aftersales.model.TemuChildrenAftersales;
import com.badfisher.temu.business.aftersales.model.TemuParentAftersales;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemuAftersalesPullServiceMappingTest {

	private final TemuAftersalesPullService service = new TemuAftersalesPullService();

	@Test
	@SuppressWarnings("unchecked")
	void shouldMapVerifiedParentDetailResponse() throws Exception {
		TemuParentAftersales parent = new TemuParentAftersales();
		parent.setParentAftersalesSn("P-D01");
		parent.setParentOrderSn("P");
		parent.setAftersalesType(2);

		Map<String, Object> goodsInfo = new HashMap<>();
		goodsInfo.put("productSkuId", 8884016910L);
		goodsInfo.put("productList",
				Collections.singletonList(Collections.<String, Object>singletonMap("productSkuId", 8884016910L)));

		Map<String, Object> child = new HashMap<>();
		child.put("afterSalesSn", "C-D01");
		child.put("orderSn", "C");
		child.put("afterSalesStatus", 6);
		child.put("applyAfterSalesGoodsNumber", 1);
		child.put("afterSalesReasonCode", 14);
		child.put("afterSalesReasonDesc", "Item defective or doesn't work");
		child.put("buyerComment", "Cannot get it to work");
		child.put("afterSalesGoodsInfo", goodsInfo);

		Map<String, Object> detail = new HashMap<>();
		detail.put("afterSalesList", Collections.singletonList(child));

		Method buildDetailChildren = TemuAftersalesPullService.class.getDeclaredMethod("buildDetailChildren", Map.class,
				TemuParentAftersales.class);
		buildDetailChildren.setAccessible(true);
		List<Map<String, Object>> normalized = (List<Map<String, Object>>) buildDetailChildren.invoke(service, detail,
				parent);

		Method buildChild = TemuAftersalesPullService.class.getDeclaredMethod("buildChildAftersales", Map.class,
				TemuAccessToken.class, Date.class);
		buildChild.setAccessible(true);
		TemuChildrenAftersales mapped = (TemuChildrenAftersales) buildChild.invoke(service, normalized.get(0),
				account(), new Date());

		assertEquals("P-D01", mapped.getParentAftersalesSn());
		assertEquals("C-D01", mapped.getAftersalesSn());
		assertEquals("C", mapped.getOrderSn());
		assertEquals(2, mapped.getAftersalesType());
		assertEquals(14, mapped.getAftersalesReasonCode());
		assertEquals(8884016910L, mapped.getProductSkuId());
		assertEquals(1, mapped.getApplyGoodsNumber());
		assertEquals(1001, mapped.getChannelId());
		assertEquals("US", mapped.getSite());
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldMapAndDeduplicateVerifiedReturnLogisticsResponse() throws Exception {
		Map<String, Object> logistics = new HashMap<>();
		logistics.put("carrierName", "FedEx");
		logistics.put("returnWarehouseType", 2);
		logistics.put("returnWarehouseRegion1Name", "United States");
		logistics.put("trackingNumber", "382143330715");

		Map<String, Object> result = new HashMap<>();
		result.put("logisticsInfoList", Arrays.asList(logistics, logistics));

		Method method = TemuAftersalesPullService.class.getDeclaredMethod("buildReturnLogistics", Map.class,
				TemuAccessToken.class, String.class);
		method.setAccessible(true);
		List<TemuAftersalesReturnLogistics> rows = (List<TemuAftersalesReturnLogistics>) method.invoke(service, result,
				account(), "P-D01");

		assertEquals(1, rows.size());
		assertEquals("FedEx", rows.get(0).getCarrierName());
		assertEquals("382143330715", rows.get(0).getTrackingNumber());
		assertEquals(2, rows.get(0).getReturnWarehouseType());
		assertEquals("United States", rows.get(0).getReturnWarehouseRegion1Name());
		assertEquals(1001, rows.get(0).getChannelId());
		assertEquals("US", rows.get(0).getSite());
	}

	@Test
	void shouldFallbackToChildProductSkuIdWhenProductValueIsNull() throws Exception {
		Map<String, Object> productMap = new HashMap<>();
		productMap.put("productSkuId", null);
		productMap.put("extCode", "SKU-001");

		Method method = TemuAftersalesPullService.class.getDeclaredMethod("buildAftersalesProduct", Map.class,
				String.class, String.class, Long.class, TemuAccessToken.class, Date.class);
		method.setAccessible(true);
		TemuAftersalesProduct product = (TemuAftersalesProduct) method.invoke(service, productMap, "P-D01", "C-D01",
				8884016910L, account(), new Date());

		assertEquals(8884016910L, product.getProductSkuId());
		assertEquals("SKU-001", product.getExtCode());
		assertEquals(1001, product.getChannelId());
		assertEquals("US", product.getSite());
	}

	@Test
	void shouldOnlyPullLogisticsForReturnStages() throws Exception {
		Method method = TemuAftersalesPullService.class.getDeclaredMethod("shouldPullReturnLogistics",
				TemuParentAftersales.class);
		method.setAccessible(true);

		TemuParentAftersales refundedReturn = parent(2, 5);
		TemuParentAftersales requestedReturn = parent(2, 10);
		TemuParentAftersales refundOnly = parent(1, 5);

		assertTrue((Boolean) method.invoke(service, refundedReturn));
		assertEquals(false, method.invoke(service, requestedReturn));
		assertEquals(false, method.invoke(service, refundOnly));
	}

	private static TemuParentAftersales parent(int type, int status) {
		TemuParentAftersales parent = new TemuParentAftersales();
		parent.setAftersalesType(type);
		parent.setParentAftersalesStatus(status);
		return parent;
	}

	private static TemuAccessToken account() {
		TemuAccessToken account = new TemuAccessToken();
		account.setSellerId("SELLER");
		account.setChannelId(1001);
		account.setSite("US");
		return account;
	}

}
