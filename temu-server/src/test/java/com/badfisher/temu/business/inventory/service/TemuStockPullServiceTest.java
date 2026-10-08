package com.badfisher.temu.business.inventory.service;

import com.badfisher.temu.business.product.service.TemuProductService;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.badfisher.temu.client.TemuApiClient;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.product.model.TemuProduct;
import com.badfisher.temu.business.inventory.model.TemuProductStock;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Temu库存拉取服务单元测试，不访问真实数据库、Redis或Temu接口。 */
@ExtendWith(MockitoExtension.class)
class TemuStockPullServiceTest {

	@Mock
	private TemuApiClient temuApiClient;

	@Mock
	private TemuProductService temuProductService;

	@Mock
	private TemuProductStockService temuProductStockService;

	@Mock
	private PlatformTransactionManager transactionManager;

	@Mock
	private TransactionStatus transactionStatus;

	private TemuStockPullService service;

	private ThreadPoolTaskExecutor apiExecutor;

	@BeforeAll
	static void initializeTableMetadata() {
		MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
		TableInfoHelper.initTableInfo(assistant, TemuProduct.class);
	}

	@BeforeEach
	void setUp() {
		apiExecutor = new ThreadPoolTaskExecutor();
		apiExecutor.setCorePoolSize(16);
		apiExecutor.setMaxPoolSize(16);
		apiExecutor.setQueueCapacity(100);
		apiExecutor.setThreadNamePrefix("stock-test-api-");
		apiExecutor.initialize();

		service = new TemuStockPullService();
		ReflectionTestUtils.setField(service, "temuApiClient", temuApiClient);
		ReflectionTestUtils.setField(service, "temuProductService", temuProductService);
		ReflectionTestUtils.setField(service, "temuProductStockService", temuProductStockService);
		ReflectionTestUtils.setField(service, "temuApiExecutor", apiExecutor);
		ReflectionTestUtils.setField(service, "transactionManager", transactionManager);
		ReflectionTestUtils.invokeMethod(service, "initTransactionTemplate");
	}

	@AfterEach
	void tearDown() {
		apiExecutor.shutdown();
	}

	@Test
	void shouldSubmitSkcRequestsInBoundedBatches() throws Exception {
		TemuAccessToken account = account();
		when(temuProductService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(products(33));
		when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);

		CountDownLatch firstBatchStarted = new CountDownLatch(5);
		CountDownLatch releaseFirstBatch = new CountDownLatch(1);
		when(temuApiClient.callByAuthRegion(eq(TemuAuthRegion.US), anyMap(), eq(account))).thenAnswer(invocation -> {
			Map<String, Object> params = invocation.getArgument(1);
			firstBatchStarted.countDown();
			assertTrue(releaseFirstBatch.await(5, TimeUnit.SECONDS));
			return stockResponse(((Number) params.get("productSkcId")).longValue());
		});

		ExecutorService caller = Executors.newSingleThreadExecutor();
		try {
			CompletableFuture<Integer> resultFuture = CompletableFuture
				.supplyAsync(() -> service.pullAccountStock(account, TemuAuthRegion.US), caller);

			assertTrue(firstBatchStarted.await(5, TimeUnit.SECONDS), "单主体第一批5个SKC应及时开始执行");
			assertEquals(0, apiExecutor.getThreadPoolExecutor().getQueue().size(), "第一批未完成前不应把后续SKC提前压入API队列");

			releaseFirstBatch.countDown();
			assertEquals(33, resultFuture.get(10, TimeUnit.SECONDS));
		}
		finally {
			releaseFirstBatch.countDown();
			caller.shutdownNow();
		}

		verify(temuApiClient, org.mockito.Mockito.times(33)).callByAuthRegion(eq(TemuAuthRegion.US), anyMap(),
				eq(account));
		verify(temuProductStockService).deleteAccountSnapshot("APP-1", "SELLER-1");

		ArgumentCaptor<List<TemuProductStock>> currentCaptor = ArgumentCaptor.forClass(List.class);
		verify(temuProductStockService).insertBatch(currentCaptor.capture());
		assertEquals(33, currentCaptor.getValue().size());
		assertTrue(currentCaptor.getValue().stream().allMatch(stock -> "测试店铺".equals(stock.getShopName())));
		assertTrue(currentCaptor.getValue()
			.stream()
			.allMatch(stock -> "APP-1".equals(stock.getAppId()) && "SELLER-1".equals(stock.getSellerId())));
		assertTrue(currentCaptor.getValue().stream().allMatch(stock -> stock.getSnapshotVersion().matches("\\d{17}")),
				"快照版本应为yyyyMMddHHmmssSSS格式");
		verify(temuProductStockService).insertHistoryBatch(currentCaptor.getValue());
	}

	@Test
	void shouldKeepPreviousSnapshotWhenAnySkcFails() {
		TemuAccessToken account = account();
		when(temuProductService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(products(2));
		when(temuApiClient.callByAuthRegion(eq(TemuAuthRegion.US), anyMap(), eq(account))).thenAnswer(invocation -> {
			Map<String, Object> params = invocation.getArgument(1);
			long skcId = ((Number) params.get("productSkcId")).longValue();
			return skcId == 2L ? null : stockResponse(skcId);
		});

		assertThrows(IllegalStateException.class, () -> service.pullAccountStock(account, TemuAuthRegion.US));

		verify(temuProductStockService, never()).deleteAccountSnapshot(any(), any());
		verify(temuProductStockService, never()).insertBatch(any());
		verify(temuProductStockService, never()).insertHistoryBatch(any());
	}

	private static TemuAccessToken account() {
		TemuAccessToken account = new TemuAccessToken();
		account.setAppId("APP-1");
		account.setSellerId("SELLER-1");
		account.setShopName("测试店铺");
		account.setAppKey("key");
		account.setAppSecret("secret");
		account.setAccessToken("token");
		account.setSite("US");
		account.setType(0);
		return account;
	}

	private static List<TemuProduct> products(int count) {
		List<TemuProduct> products = new ArrayList<>();
		for (long id = 1; id <= count; id++) {
			TemuProduct product = new TemuProduct();
			product.setAppId("APP-1");
			product.setSellerId("SELLER-1");
			product.setProductSkcId(id);
			products.add(product);
		}
		return products;
	}

	private static Map<String, Object> stockResponse(long skcId) {
		Map<String, Object> stock = new HashMap<>();
		stock.put("productSkuId", skcId + 10_000L);
		stock.put("skuStockQuantity", 20);
		stock.put("enablePreSale", false);
		stock.put("warehouseId", "WH-1");
		stock.put("warehouseName", "测试仓");
		stock.put("shippingMode", 1);
		stock.put("tempLockQuantity", 0);

		Map<String, Object> result = new HashMap<>();
		List<Map<String, Object>> stocks = new ArrayList<>();
		stocks.add(stock);
		result.put("productSkuStockList", stocks);

		Map<String, Object> response = new HashMap<>();
		response.put("result", result);
		return response;
	}

}
