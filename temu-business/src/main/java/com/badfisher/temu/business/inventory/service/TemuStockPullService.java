package com.badfisher.temu.business.inventory.service;

import com.badfisher.temu.business.product.service.TemuProductService;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.badfisher.temu.client.TemuApiClient;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.product.model.TemuProduct;
import com.badfisher.temu.business.inventory.model.TemuProductStock;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.badfisher.temu.client.enums.TemuApiType;
import com.badfisher.temu.business.sync.support.RetryHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Temu商品库存拉取服务 — 按商品SKC并发调用 bg.btg.goods.stock.quantity.get。
 * <p>
 * HTTP请求统一提交到temuApiExecutor；每次请求仍经过TemuApiClient中按App ID隔离的
 * 分布式限流器。全部SKC成功后才整体替换当前快照，任一请求失败均保留上一版快照。
 *
 * @author lenkor
 */
@Slf4j
@Service
public class TemuStockPullService {

	private static final int DB_BATCH_SIZE = 500;

	/**
	 * 单店铺每批最多提交的库存请求数，与 temuApiExecutor 最大线程数保持一致。 每批完成后再提交下一批，避免大店铺一次性占满线程池队列。
	 */
	/** 单主体并发5个请求，三个主体同时执行时总并发为15，给共享API线程池保留1个线程余量。 */
	private static final int API_BATCH_SIZE = 5;

	@Resource
	private TemuApiClient temuApiClient;

	@Resource
	private TemuProductService temuProductService;

	@Resource
	private TemuProductStockService temuProductStockService;

	@Resource(name = "temuApiExecutor")
	private ThreadPoolTaskExecutor temuApiExecutor;

	@Resource
	private PlatformTransactionManager transactionManager;

	private TransactionTemplate transactionTemplate;

	@PostConstruct
	private void initTransactionTemplate() {
		transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/**
	 * 拉取一个商品授权账号下的全部SKC库存。
	 * @return 成功落库的SKU仓库库存条数；无商品返回0
	 */
	public int pullAccountStock(TemuAccessToken account, TemuAuthRegion authRegion) {
		if (account.getAppId() == null || account.getSellerId() == null) {
			throw new IllegalArgumentException("库存拉取缺少appId/sellerId");
		}
		List<Long> productSkcIds = temuProductService
			.list(Wrappers.lambdaQuery(TemuProduct.class)
				.select(TemuProduct::getProductSkcId)
				.eq(TemuProduct::getAppId, account.getAppId())
				.eq(TemuProduct::getSellerId, account.getSellerId())
				.isNotNull(TemuProduct::getProductSkcId))
			.stream()
			.map(TemuProduct::getProductSkcId)
			.distinct()
			.collect(Collectors.toList());
		if (productSkcIds.isEmpty()) {
			log.info("店铺 {} 无商品SKC，跳过库存拉取", account.getSellerId());
			return 0;
		}

		Date snapshotTime = new Date();
		// 时间型版本号可直接按字符串排序，例如：20260715142330125。
		String snapshotVersion = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(snapshotTime);
		List<TemuProductStock> allStocks = new ArrayList<>();
		int failedCount = 0;

		for (int start = 0; start < productSkcIds.size(); start += API_BATCH_SIZE) {
			int end = Math.min(start + API_BATCH_SIZE, productSkcIds.size());
			List<CompletableFuture<StockFetchResult>> batchFutures = new ArrayList<>(end - start);
			for (Long productSkcId : productSkcIds.subList(start, end)) {
				batchFutures.add(CompletableFuture.supplyAsync(
						() -> fetchStock(account, authRegion, productSkcId, snapshotVersion, snapshotTime),
						temuApiExecutor));
			}

			try {
				CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();
			}
			catch (Exception e) {
				// 继续逐个读取结果，以便准确统计异常的SKC数量并保留成功结果。
				log.error("店铺 {} 库存批次执行存在异常: batch={}-{}", account.getSellerId(), start + 1, end, e);
			}

			for (CompletableFuture<StockFetchResult> future : batchFutures) {
				try {
					StockFetchResult result = future.join();
					if (!result.success) {
						failedCount++;
					}
					else {
						allStocks.addAll(result.stocks);
					}
				}
				catch (Exception e) {
					failedCount++;
					log.error("店铺 {} 库存并发任务执行异常", account.getSellerId(), e);
				}
			}
		}

		if (failedCount > 0) {
			throw new IllegalStateException("库存拉取存在 " + failedCount + " 个SKC失败，已取消快照清理");
		}

		transactionTemplate.executeWithoutResult(status -> {
			// 当前快照替换和历史快照追加位于同一事务，任一写入失败均回滚到上一版。
			temuProductStockService.deleteAccountSnapshot(account.getAppId(), account.getSellerId());
			for (int start = 0; start < allStocks.size(); start += DB_BATCH_SIZE) {
				int end = Math.min(start + DB_BATCH_SIZE, allStocks.size());
				List<TemuProductStock> batch = allStocks.subList(start, end);
				temuProductStockService.insertBatch(batch);
				temuProductStockService.insertHistoryBatch(batch);
			}
		});

		log.info("店铺 {} 库存快照完成: version={}, SKC数={}, 库存明细数={}", account.getSellerId(), snapshotVersion,
				productSkcIds.size(), allStocks.size());
		return allStocks.size();
	}

	@SuppressWarnings("unchecked")
	private StockFetchResult fetchStock(TemuAccessToken account, TemuAuthRegion authRegion, Long productSkcId,
			String snapshotVersion, Date snapshotTime) {
		try {
			Map<String, Object> params = new HashMap<>();
			params.put("type", TemuApiType.LOCAL_GOODS_STOCK_QUANTITY_GET.getType());
			params.put("productSkcId", productSkcId);

			Map<String, Object> response = RetryHelper.withRetry(
					"店铺 " + account.getSellerId() + " SKC " + productSkcId + " 库存",
					() -> temuApiClient.callByAuthRegion(authRegion, params, account));
			if (response == null) {
				return StockFetchResult.failed();
			}
			Object resultObj = response.get("result");
			if (!(resultObj instanceof Map)) {
				log.warn("店铺 {} SKC {} 库存响应缺少result节点", account.getSellerId(), productSkcId);
				return StockFetchResult.failed();
			}
			Object stockListObj = ((Map<String, Object>) resultObj).get("productSkuStockList");
			if (!(stockListObj instanceof List)) {
				return StockFetchResult.success(Collections.emptyList());
			}

			List<TemuProductStock> stocks = new ArrayList<>();
			Date now = new Date();
			for (Object itemObj : (List<?>) stockListObj) {
				if (!(itemObj instanceof Map)) {
					continue;
				}
				Map<String, Object> item = (Map<String, Object>) itemObj;
				Long productSkuId = longVal(item.get("productSkuId"));
				String warehouseId = str(item.get("warehouseId"));
				if (productSkuId == null || warehouseId == null) {
					log.warn("店铺 {} SKC {} 库存记录缺少SKU或仓库ID: {}", account.getSellerId(), productSkcId, item);
					continue;
				}
				TemuProductStock stock = new TemuProductStock();
				stock.setAppId(account.getAppId());
				stock.setSellerId(account.getSellerId());
				stock.setShopName(account.getShopName());
				stock.setProductSkcId(productSkcId);
				stock.setProductSkuId(productSkuId);
				stock.setWarehouseId(warehouseId);
				stock.setWarehouseName(str(item.get("warehouseName")));
				stock.setStockQuantity(intVal(item.get("skuStockQuantity")));
				stock.setTempLockQuantity(intVal(item.get("tempLockQuantity")));
				stock.setEnablePreSale(boolVal(item.get("enablePreSale")));
				stock.setShippingMode(intVal(item.get("shippingMode")));
				stock.setSnapshotVersion(snapshotVersion);
				stock.setSnapshotTime(snapshotTime);
				stock.setCreateTime(now);
				stock.setUpdateTime(now);
				stocks.add(stock);
			}
			return StockFetchResult.success(stocks);
		}
		catch (Exception e) {
			log.error("店铺 {} SKC {} 库存拉取失败", account.getSellerId(), productSkcId, e);
			return StockFetchResult.failed();
		}
	}

	private static String str(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private static Long longVal(Object value) {
		if (value == null)
			return null;
		try {
			return Long.valueOf(String.valueOf(value));
		}
		catch (Exception e) {
			return null;
		}
	}

	private static Integer intVal(Object value) {
		if (value == null)
			return null;
		try {
			return Integer.valueOf(String.valueOf(value));
		}
		catch (Exception e) {
			return null;
		}
	}

	private static Integer boolVal(Object value) {
		if (value == null)
			return null;
		if (value instanceof Boolean)
			return (Boolean) value ? 1 : 0;
		return "true".equalsIgnoreCase(String.valueOf(value)) ? 1 : 0;
	}

	private static final class StockFetchResult {

		private final boolean success;

		private final List<TemuProductStock> stocks;

		private StockFetchResult(boolean success, List<TemuProductStock> stocks) {
			this.success = success;
			this.stocks = stocks;
		}

		private static StockFetchResult success(List<TemuProductStock> stocks) {
			return new StockFetchResult(true, stocks);
		}

		private static StockFetchResult failed() {
			return new StockFetchResult(false, Collections.emptyList());
		}

	}

}
