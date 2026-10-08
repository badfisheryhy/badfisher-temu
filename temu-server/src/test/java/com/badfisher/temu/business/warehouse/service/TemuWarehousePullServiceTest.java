package com.badfisher.temu.business.warehouse.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.badfisher.temu.client.TemuApiClient;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.warehouse.model.TemuWarehouse;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Temu仓库共享授权拉取单元测试，不访问真实DB或Temu接口。 */
@ExtendWith(MockitoExtension.class)
class TemuWarehousePullServiceTest {

	@Mock
	private TemuApiClient temuApiClient;

	@Mock
	private TemuWarehouseService temuWarehouseService;

	@Mock
	private PlatformTransactionManager transactionManager;

	@Mock
	private TransactionStatus transactionStatus;

	private TemuWarehousePullService service;

	@BeforeAll
	static void initializeTableMetadata() {
		MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
		TableInfoHelper.initTableInfo(assistant, TemuWarehouse.class);
	}

	@BeforeEach
	void setUp() {
		service = new TemuWarehousePullService();
		ReflectionTestUtils.setField(service, "temuApiClient", temuApiClient);
		ReflectionTestUtils.setField(service, "temuWarehouseService", temuWarehouseService);
		ReflectionTestUtils.setField(service, "transactionManager", transactionManager);
		ReflectionTestUtils.invokeMethod(service, "initTransactionTemplate");
	}

	@Test
	void globalShouldCallApiOnceAndPersistWarehouseWithoutChannelDuplication() {
		TemuAccessToken au = account(30L, 1581, "AU");
		when(temuApiClient.callByAuthRegion(eq(TemuAuthRegion.GLOBAL), anyMap(), eq(au)))
			.thenReturn(responseWithWarehouse("WH-1"));
		when(temuWarehouseService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());
		when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
		when(temuWarehouseService.saveOrUpdateBatch(any())).thenReturn(true);

		int persisted = service.doPullTask(au, TemuAuthRegion.GLOBAL);

		assertEquals(1, persisted);
		verify(temuApiClient, times(1)).callByAuthRegion(eq(TemuAuthRegion.GLOBAL), anyMap(), eq(au));
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<TemuWarehouse>> rowsCaptor = ArgumentCaptor.forClass(List.class);
		verify(temuWarehouseService).saveOrUpdateBatch(rowsCaptor.capture());
		List<TemuWarehouse> rows = rowsCaptor.getValue();
		assertEquals(1, rows.size());
		assertEquals("SELLER-1", rows.get(0).getSellerId());
		assertEquals(Collections.singleton("WH-1"),
				rows.stream().map(TemuWarehouse::getWarehouseId).collect(Collectors.toSet()));
	}

	private static Map<String, Object> responseWithWarehouse(String warehouseId) {
		Map<String, Object> warehouse = new HashMap<>();
		warehouse.put("warehouseId", warehouseId);
		warehouse.put("warehouseName", "Global Warehouse");
		warehouse.put("regionId1", 211);
		Map<String, Object> result = new HashMap<>();
		result.put("warehouseList", Collections.singletonList(warehouse));
		Map<String, Object> response = new HashMap<>();
		response.put("result", result);
		return response;
	}

	private static TemuAccessToken account(Long id, Integer channelId, String site) {
		TemuAccessToken account = new TemuAccessToken();
		account.setId(id);
		account.setAppId("DEMO-APP-A");
		account.setSellerId("SELLER-1");
		account.setAuthRegion(TemuAuthRegion.GLOBAL.getCode());
		account.setType(1);
		account.setChannelId(channelId);
		account.setSite(site);
		account.setAppKey("APP-KEY");
		account.setAppSecret("APP-SECRET");
		account.setAccessToken("ACCESS-TOKEN");
		account.setDelFlag(0);
		return account;
	}

}
