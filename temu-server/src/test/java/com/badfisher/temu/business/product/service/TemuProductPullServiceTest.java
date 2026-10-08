package com.badfisher.temu.business.product.service;

import com.badfisher.temu.business.sync.service.SyncTaskService;
import com.badfisher.temu.business.sync.execution.TaskLeaseLostException;

import com.badfisher.temu.client.TemuApiClient;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.product.model.TemuProduct;
import com.badfisher.temu.business.product.model.TemuProductSku;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TemuProductPullServiceTest {

	@Mock
	private TemuApiClient temuApiClient;

	@Mock
	private TemuProductService temuProductService;

	@Mock
	private TemuProductSkuService temuProductSkuService;

	@Mock
	private SyncTaskService temuOrderRequestTaskService;

	@Mock
	private PlatformTransactionManager transactionManager;

	@Mock
	private TransactionStatus transactionStatus;

	private TemuProductPullService service;

	@BeforeEach
	void setUp() {
		service = new TemuProductPullService();
		ReflectionTestUtils.setField(service, "temuApiClient", temuApiClient);
		ReflectionTestUtils.setField(service, "temuProductService", temuProductService);
		ReflectionTestUtils.setField(service, "temuProductSkuService", temuProductSkuService);
		ReflectionTestUtils.setField(service, "temuOrderRequestTaskService", temuOrderRequestTaskService);
		ReflectionTestUtils.setField(service, "transactionManager", transactionManager);
		ReflectionTestUtils.invokeMethod(service, "initTransactionTemplate");
	}

	@Test
	void shouldPersistAndReconcileByAppAndSellerWithoutChannel() {
		TemuAccessToken account = new TemuAccessToken();
		account.setAppId("DEMO-APP-A");
		account.setSellerId("SELLER-1");
		account.setShopName("测试店铺");
		SyncTask task = new SyncTask();
		task.setId(99L);
		task.setExecutionId("execution-a");

		when(temuApiClient.callByAuthRegion(eq(TemuAuthRegion.CN), anyMap(), eq(account)))
			.thenReturn(productResponse());
		when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
		when(temuOrderRequestTaskService.completeTask(99L, "execution-a")).thenReturn(true);

		service.doPullTask(account, task, TemuAuthRegion.CN);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<TemuProduct>> productCaptor = ArgumentCaptor.forClass(List.class);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<TemuProductSku>> skuCaptor = ArgumentCaptor.forClass(List.class);
		verify(temuProductService).insertOrUpdateBatch(productCaptor.capture());
		verify(temuProductSkuService).insertOrUpdateBatch(skuCaptor.capture());
		assertEquals("DEMO-APP-A", productCaptor.getValue().get(0).getAppId());
		assertEquals("SELLER-1", productCaptor.getValue().get(0).getSellerId());
		assertEquals("DEMO-APP-A", skuCaptor.getValue().get(0).getAppId());
		assertEquals("SELLER-1", skuCaptor.getValue().get(0).getSellerId());
		verify(temuProductSkuService).markMissingDeleted("DEMO-APP-A", "SELLER-1", 99L);
		verify(temuProductService).markMissingDeleted("DEMO-APP-A", "SELLER-1", 99L);
	}

	@Test
	void leaseLostDuringRequestShouldPreventPageWriteAndSnapshotCleanup() {
		TemuAccessToken account = new TemuAccessToken();
		account.setAppId("demo-app");
		account.setSellerId("demo-seller");
		SyncTask task = new SyncTask();
		task.setId(99L);
		task.setExecutionId("old-execution");
		when(temuApiClient.callByAuthRegion(eq(TemuAuthRegion.CN), anyMap(), eq(account)))
			.thenReturn(productResponse());
		when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
		doThrow(new TaskLeaseLostException("lease lost during HTTP request"))
			.when(temuOrderRequestTaskService).lockExecution(task);

		assertThrows(TaskLeaseLostException.class, () -> service.doPullTask(account, task, TemuAuthRegion.CN));

		verifyNoInteractions(temuProductService, temuProductSkuService);
		verify(transactionManager).rollback(transactionStatus);
		verify(temuApiClient).callByAuthRegion(eq(TemuAuthRegion.CN), anyMap(), eq(account));
		verify(temuOrderRequestTaskService, never()).completeTask(any(), any());
	}

	@Test
	void leaseLostBeforePullShouldPreventApiCalls() {
		SyncTask task = new SyncTask();
		doThrow(new TaskLeaseLostException("lease lost"))
			.when(temuOrderRequestTaskService).checkExecution(task);

		assertThrows(TaskLeaseLostException.class,
				() -> service.doPullTask(new TemuAccessToken(), task, TemuAuthRegion.CN));

		verifyNoInteractions(temuApiClient, temuProductService, temuProductSkuService, transactionManager);
	}

	private static Map<String, Object> productResponse() {
		Map<String, Object> sku = new HashMap<>();
		sku.put("productSkuId", 3001L);

		Map<String, Object> product = new HashMap<>();
		product.put("productId", 1001L);
		product.put("productSkcId", 2001L);
		product.put("productSkuSummaries", Collections.singletonList(sku));

		Map<String, Object> result = new HashMap<>();
		result.put("data", Collections.singletonList(product));
		result.put("totalCount", 1);

		Map<String, Object> response = new HashMap<>();
		response.put("result", result);
		return response;
	}

}
