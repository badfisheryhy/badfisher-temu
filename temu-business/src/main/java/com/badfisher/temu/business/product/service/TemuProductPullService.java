package com.badfisher.temu.business.product.service;

import com.badfisher.temu.business.sync.service.SyncTaskService;
import com.badfisher.temu.business.sync.execution.TaskLeaseLostException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.badfisher.temu.client.TemuApiClient;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.product.model.TemuProduct;
import com.badfisher.temu.business.product.model.TemuProductSku;
import com.badfisher.temu.business.sync.support.PageResult;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.badfisher.temu.client.enums.TemuApiType;
import com.badfisher.temu.client.exception.TemuApiException;
import com.badfisher.temu.business.sync.support.RetryHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;

/**
 * Temu 商品档案拉取服务 — 分页调用 bg.glo.goods.list.get 并同步商品及SKU。
 * <p>
 * 接口没有可靠的更新时间筛选条件，因此首次和后续任务均执行完整快照扫描；落库通过唯一键 批量 upsert，只新增或覆盖变化的数据。整轮分页全部成功后，再按
 * syncTaskId 软删除本次 未返回的历史记录。分页中途失败不会执行快照清理，避免误删已有数据。
 *
 * @author lenkor
 */
@Slf4j
@Service
public class TemuProductPullService {

	private static final int PAGE_SIZE = 100;

	private static final int MAX_PAGE_CAP = 1000;

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Resource
	private TemuApiClient temuApiClient;

	@Resource
	private TemuProductService temuProductService;

	@Resource
	private TemuProductSkuService temuProductSkuService;

	@Resource
	private SyncTaskService temuOrderRequestTaskService;

	@Resource
	private PlatformTransactionManager transactionManager;

	private TransactionTemplate transactionTemplate;

	@PostConstruct
	private void initTransactionTemplate() {
		transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/** 执行一个商品快照任务；首次任务完成全量建库，后续任务完成增量更新和删除对账。 */
	public void doPullTask(TemuAccessToken account, SyncTask task, TemuAuthRegion authRegion) {
		String sellerId = account.getSellerId();
		try {
			temuOrderRequestTaskService.checkExecution(task);
			int total = pullAllPages(account, task, authRegion);
			transactionTemplate.executeWithoutResult(status -> {
				temuOrderRequestTaskService.lockExecution(task);
				// 只有完整分页成功后才能清理缺失数据，避免接口中途失败造成误删除。
				temuProductSkuService.markMissingDeleted(account.getAppId(), account.getSellerId(), task.getId());
				temuProductService.markMissingDeleted(account.getAppId(), account.getSellerId(), task.getId());
			});
			markTaskComplete(task);
			log.info("店铺 {} 商品同步完成: 本轮处理 {} 条", sellerId, total);
		}
		catch (TaskLeaseLostException e) {
			throw e;
		}
		catch (TemuApiException e) {
			log.error("店铺 {} 商品同步失败: code={}, msg={}", sellerId, e.getErrorCode(), e.getMessage(), e);
			markTaskFailed(task, e.getMessage());
		}
		catch (Exception e) {
			log.error("店铺 {} 商品同步失败: {}", sellerId, e.getMessage(), e);
			markTaskFailed(task, e.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	private int pullAllPages(TemuAccessToken account, SyncTask task, TemuAuthRegion authRegion) {
		int totalProcessed = 0;
		int maxPage = MAX_PAGE_CAP;

		for (int page = 1; page <= maxPage; page++) {
			PageResult pageResult = fetchProductPage(page, account, authRegion, task);

			if (pageResult.isFailed()) {
				throw new IllegalStateException("商品第 " + page + " 页重试后仍拉取失败");
			}
			if (pageResult.isEmpty()) {
				break;
			}
			if (pageResult.getTotal() > 0) {
				maxPage = Math.min(MAX_PAGE_CAP, (int) Math.ceil((double) pageResult.getTotal() / PAGE_SIZE));
			}

			totalProcessed += savePage(pageResult.getItems(), account, task);
			if (pageResult.isLastPage()) {
				break;
			}
		}
		if (maxPage == MAX_PAGE_CAP && totalProcessed >= MAX_PAGE_CAP * PAGE_SIZE) {
			throw new IllegalStateException("商品分页达到安全上限 " + MAX_PAGE_CAP);
		}
		return totalProcessed;
	}

	/**
	 * 拉取单页商品并返回成功、空页、失败三态，避免用 null 同时表达多种含义。 缺少 result 节点属于响应结构异常，按失败处理，防止后续快照清理误删数据。
	 */
	@SuppressWarnings("unchecked")
	private PageResult fetchProductPage(int page, TemuAccessToken account, TemuAuthRegion authRegion, SyncTask task) {
		Map<String, Object> response = RetryHelper.withRetry("店铺 " + account.getSellerId() + " 商品第 " + page + " 页",
				() -> {
					temuOrderRequestTaskService.checkExecution(task);
					return temuApiClient.callByAuthRegion(authRegion, buildParams(page), account);
				});
		if (response == null) {
			return PageResult.failed();
		}

		Map<String, Object> result = extractResult(response);
		if (result == null) {
			log.warn("店铺 {} 商品第 {} 页缺少 result 节点", account.getSellerId(), page);
			return PageResult.failed();
		}

		List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("data");
		if (items == null || items.isEmpty()) {
			return PageResult.empty();
		}
		return PageResult.success(items, intVal(result.get("totalCount"), 0), PAGE_SIZE);
	}

	private Map<String, Object> buildParams(int page) {
		Map<String, Object> params = new HashMap<>();
		params.put("type", TemuApiType.LOCAL_GOODS_LIST_QUERY.getType());
		params.put("pageNumber", page);
		params.put("pageSize", PAGE_SIZE);
		return params;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> extractResult(Map<String, Object> response) {
		Object result = response.get("result");
		if (result instanceof Map) {
			return (Map<String, Object>) result;
		}
		Object data = response.get("data");
		if (data instanceof Map) {
			Object inner = ((Map<String, Object>) data).get("result");
			if (inner instanceof Map) {
				return (Map<String, Object>) inner;
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private int savePage(List<Map<String, Object>> items, TemuAccessToken account, SyncTask task) {
		Long taskId = task.getId();
		List<TemuProduct> products = new ArrayList<>(items.size());
		List<TemuProductSku> skus = new ArrayList<>();
		Date now = new Date();

		for (Map<String, Object> item : items) {
			Long productId = longVal(item.get("productId"));
			if (productId == null) {
				log.warn("店铺 {} 商品记录缺少 productId，跳过: {}", account.getSellerId(), item);
				continue;
			}
			TemuProduct product = buildProduct(item, account, taskId, now);
			products.add(product);

			Object skuList = item.get("productSkuSummaries");
			if (skuList instanceof List) {
				for (Object skuItem : (List<?>) skuList) {
					if (!(skuItem instanceof Map)) {
						continue;
					}
					TemuProductSku sku = buildSku((Map<String, Object>) skuItem, product, taskId, now);
					if (sku.getProductSkuId() != null) {
						skus.add(sku);
					}
				}
			}
		}

		transactionTemplate.executeWithoutResult(status -> {
			temuOrderRequestTaskService.lockExecution(task);
			if (!products.isEmpty()) {
				temuProductService.insertOrUpdateBatch(products);
			}
			if (!skus.isEmpty()) {
				temuProductSkuService.insertOrUpdateBatch(skus);
			}
		});
		return products.size();
	}

	@SuppressWarnings("unchecked")
	private TemuProduct buildProduct(Map<String, Object> item, TemuAccessToken account, Long taskId, Date now) {
		TemuProduct product = new TemuProduct();
		product.setAppId(account.getAppId());
		product.setSellerId(account.getSellerId());
		product.setShopName(account.getShopName());
		product.setProductId(longVal(item.get("productId")));
		product.setProductSkcId(longVal(item.get("productSkcId")));
		product.setProductName(str(item.get("productName")));
		product.setExtCode(str(item.get("extCode")));
		product.setMainImageUrl(str(item.get("mainImageUrl")));
		product.setSkcSiteStatus(intVal(item.get("skcSiteStatus"), null));
		product.setSupportPersonalization(boolVal(item.get("isSupportPersonalization")));
		product.setMatchSkcJitMode(boolVal(item.get("matchSkcJitMode")));
		product.setCreatedAtApi(longVal(item.get("createdAt")));

		Object leafCat = item.get("leafCat");
		if (leafCat instanceof Map) {
			Map<String, Object> cat = (Map<String, Object>) leafCat;
			product.setLeafCatId(longVal(cat.get("catId")));
			product.setLeafCatName(str(cat.get("catName")));
		}
		product.setPropertiesJson(json(item.get("productProperties")));
		Map<String, Object> extension = new LinkedHashMap<>();
		extension.put("categories", item.get("categories"));
		extension.put("productJitMode", item.get("productJitMode"));
		extension.put("productSemiManaged", item.get("productSemiManaged"));
		product.setExtensionJson(json(extension));
		product.setSyncTaskId(taskId);
		product.setDelFlag(0);
		product.setCreateTime(now);
		product.setUpdateTime(now);
		return product;
	}

	@SuppressWarnings("unchecked")
	private TemuProductSku buildSku(Map<String, Object> item, TemuProduct product, Long taskId, Date now) {
		TemuProductSku sku = new TemuProductSku();
		sku.setAppId(product.getAppId());
		sku.setSellerId(product.getSellerId());
		sku.setShopName(product.getShopName());
		sku.setProductId(product.getProductId());
		sku.setProductSkcId(product.getProductSkcId());
		sku.setProductSkuId(longVal(item.get("productSkuId")));
		sku.setExtCode(str(item.get("extCode")));
		sku.setVirtualStock(intVal(item.get("virtualStock"), null));
		Object saleAttr = item.get("productSkuSaleExtAttr");
		if (saleAttr instanceof Map) {
			sku.setShippingMode(intVal(((Map<String, Object>) saleAttr).get("productSkuShippingMode"), null));
		}
		Object warehouseAttr = item.get("productSkuWhExtAttr");
		Map<String, Object> wh = warehouseAttr instanceof Map ? (Map<String, Object>) warehouseAttr
				: Collections.emptyMap();
		Object weight = wh.get("productSkuWeight");
		if (weight instanceof Map) {
			sku.setWeight(longVal(((Map<String, Object>) weight).get("value")));
		}
		Object volume = wh.get("productSkuVolume");
		if (volume instanceof Map) {
			Map<String, Object> volumeMap = (Map<String, Object>) volume;
			sku.setPackageLength(intVal(volumeMap.get("len"), null));
			sku.setPackageWidth(intVal(volumeMap.get("width"), null));
			sku.setPackageHeight(intVal(volumeMap.get("height"), null));
		}
		Object sensitiveAttr = wh.get("productSkuSensitiveAttr");
		if (sensitiveAttr instanceof Map) {
			sku.setSensitiveFlag(intVal(((Map<String, Object>) sensitiveAttr).get("isSensitive"), null));
		}
		Map<String, Object> extension = new LinkedHashMap<>();
		extension.put("specList", item.get("productSkuSpecList"));
		extension.put("barCodes", wh.get("productSkuBarCodes"));
		extension.put("sensitiveAttr", sensitiveAttr);
		extension.put("newSensitiveAttr", wh.get("productSkuNewSensitiveAttr"));
		extension.put("sensitiveLimit", wh.get("productSkuSensitiveLimit"));
		sku.setExtensionJson(json(extension));
		sku.setSyncTaskId(taskId);
		sku.setDelFlag(0);
		sku.setCreateTime(now);
		sku.setUpdateTime(now);
		return sku;
	}

	private String json(Object value) {
		if (value == null)
			return null;
		try {
			return OBJECT_MAPPER.writeValueAsString(value);
		}
		catch (Exception e) {
			throw new IllegalArgumentException("商品JSON序列化失败", e);
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

	private static Integer intVal(Object value, Integer defaultValue) {
		if (value == null)
			return defaultValue;
		try {
			return Integer.valueOf(String.valueOf(value));
		}
		catch (Exception e) {
			return defaultValue;
		}
	}

	private static Integer boolVal(Object value) {
		if (value == null)
			return null;
		if (value instanceof Boolean)
			return (Boolean) value ? 1 : 0;
		return "true".equalsIgnoreCase(String.valueOf(value)) ? 1 : 0;
	}

	private void markTaskComplete(SyncTask task) {
		if (!temuOrderRequestTaskService.completeTask(task.getId(), task.getExecutionId())) {
			throw new TaskLeaseLostException("任务完成回写被拒绝: taskId=" + task.getId());
		}
	}

	private void markTaskFailed(SyncTask task, String message) {
		if (!temuOrderRequestTaskService.failTask(task.getId(), task.getExecutionId(), message)) {
			log.warn("商品任务 {} 失败状态回写被忽略，任务可能已被超时回收", task.getId());
		}
	}

}
