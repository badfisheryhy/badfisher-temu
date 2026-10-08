package com.badfisher.temu.server.integration;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.badfisher.temu.business.account.service.TemuGlobalAccountResolver;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.sync.service.SyncTaskService;
import com.badfisher.temu.business.sync.execution.TaskLeaseLostException;
import com.badfisher.temu.business.order.service.TemuChildrenOrderService;
import com.badfisher.temu.business.order.service.TemuOrderAddressService;
import com.badfisher.temu.business.order.service.TemuOrderProductService;
import com.badfisher.temu.business.order.service.TemuOrderPullService;
import com.badfisher.temu.business.order.service.TemuParentOrderService;

import com.badfisher.temu.client.TemuApiClient;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemuOrderPullServiceTransactionTest {

	@Mock
	private TemuApiClient temuApiClient;

	@Mock
	private TemuParentOrderService temuParentOrderService;

	@Mock
	private TemuChildrenOrderService temuChildrenOrderService;

	@Mock
	private TemuOrderAddressService temuOrderAddressService;

	@Mock
	private TemuOrderProductService temuOrderProductService;

	@Mock
	private PlatformTransactionManager transactionManager;

	@Mock
	private TransactionStatus transactionStatus;

	@Mock
	private SyncTaskService taskService;

	private TemuOrderPullService service;

	private final SyncTask task = new SyncTask();

	@BeforeEach
	void setUp() {
		service = new TemuOrderPullService();
		ReflectionTestUtils.setField(service, "temuOrderRequestTaskService", taskService);
		ReflectionTestUtils.setField(service, "temuApiClient", temuApiClient);
		ReflectionTestUtils.setField(service, "temuParentOrderService", temuParentOrderService);
		ReflectionTestUtils.setField(service, "temuChildrenOrderService", temuChildrenOrderService);
		ReflectionTestUtils.setField(service, "temuOrderAddressService", temuOrderAddressService);
		ReflectionTestUtils.setField(service, "temuOrderProductService", temuOrderProductService);
		ReflectionTestUtils.setField(service, "temuGlobalAccountResolver", new TemuGlobalAccountResolver());
		ReflectionTestUtils.setField(service, "transactionManager", transactionManager);
		ReflectionTestUtils.invokeMethod(service, "initTransactionTemplate");

	}

	@Test
	void shouldPersistParentBeforeChildrenAndProductsInOneTransaction() {
		int processed = processPage();

		assertEquals(1, processed);
		InOrder order = inOrder(temuParentOrderService, temuChildrenOrderService, temuOrderProductService,
				transactionManager, taskService);
		order.verify(taskService).lockExecution(task);
		order.verify(temuParentOrderService).insertOrUpdateBatch(any());
		order.verify(temuChildrenOrderService).insertOrUpdateBatch(any());
		order.verify(temuOrderProductService).insertOrUpdateBatch(any());
		order.verify(transactionManager).commit(transactionStatus);
	}

	@Test
	void shouldRollbackWholePageWhenParentPersistenceFails() {
		doThrow(new IllegalStateException("parent write failed")).when(temuParentOrderService)
			.insertOrUpdateBatch(any());

		assertThrows(IllegalStateException.class, this::processPage);

		verify(temuChildrenOrderService, never()).insertOrUpdateBatch(any());
		verify(temuOrderProductService, never()).insertOrUpdateBatch(any());
		verify(transactionManager).rollback(transactionStatus);
		verify(transactionManager, never()).commit(transactionStatus);
	}

	@Test
	void globalOrderWithoutRegionalMappingShouldRejectWholePage() {
		TemuAccessToken requestAccount = account();
		requestAccount.setAppId("DEMO-APP-B");
		requestAccount.setSite("AU");
		requestAccount.setAuthRegion("GLOBAL");

		assertThrows(IllegalStateException.class,
				() -> ReflectionTestUtils.invokeMethod(service, "processOrderPage", pageItems(), requestAccount,
						TemuAuthRegion.GLOBAL, Collections.emptyMap(), task));

		verify(temuParentOrderService, never()).insertOrUpdateBatch(any());
		verify(temuChildrenOrderService, never()).insertOrUpdateBatch(any());
		verify(temuOrderProductService, never()).insertOrUpdateBatch(any());
		verify(transactionManager, never()).getTransaction(any());
	}

	@Test
	void staleExecutionShouldRollbackBeforeAnyBusinessWrite() {
		doThrow(new TaskLeaseLostException("lease lost")).when(taskService).lockExecution(task);

		assertThrows(TaskLeaseLostException.class, this::processPage);

		verify(temuParentOrderService, never()).insertOrUpdateBatch(any());
		verify(temuChildrenOrderService, never()).insertOrUpdateBatch(any());
		verify(temuOrderProductService, never()).insertOrUpdateBatch(any());
		verify(transactionManager).rollback(transactionStatus);
	}

	@SuppressWarnings("unchecked")
	private int processPage() {
		when(temuParentOrderService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
		when(temuOrderAddressService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
		when(temuChildrenOrderService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
		when(temuOrderProductService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
		when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
		Integer result = ReflectionTestUtils.invokeMethod(service, "processOrderPage", pageItems(), account(),
				TemuAuthRegion.US, task);
		return result == null ? 0 : result;
	}

	private static TemuAccessToken account() {
		TemuAccessToken account = new TemuAccessToken();
		account.setSellerId("SELLER-1");
		account.setChannelId(1001);
		account.setSite("US");
		return account;
	}

	private static List<Map<String, Object>> pageItems() {
		Map<String, Object> product = new HashMap<>();
		product.put("productId", 2001L);
		product.put("productSkuId", 3001L);

		Map<String, Object> child = new HashMap<>();
		child.put("orderSn", "ORDER-1");
		child.put("productList", Collections.singletonList(product));

		Map<String, Object> parent = new HashMap<>();
		parent.put("parentOrderSn", "PARENT-1");
		parent.put("parentOrderStatus", "SHIPPED");
		parent.put("siteId", 101);

		Map<String, Object> pageItem = new HashMap<>();
		pageItem.put("parentOrderMap", parent);
		pageItem.put("orderList", Collections.singletonList(child));
		return Collections.singletonList(pageItem);
	}

}
