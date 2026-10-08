package com.badfisher.temu.business.order.service;

import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.account.service.TemuGlobalAccountResolver;
import com.badfisher.temu.business.order.model.TemuChildrenOrder;
import com.badfisher.temu.business.order.model.TemuOrderAddress;
import com.badfisher.temu.business.order.model.TemuOrderProduct;
import com.badfisher.temu.business.order.model.TemuParentOrder;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.sync.service.SyncTaskService;
import com.badfisher.temu.business.sync.execution.TaskLeaseLostException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.badfisher.temu.client.TemuApiClient;
import com.badfisher.temu.business.sync.support.PageResult;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.badfisher.temu.client.enums.TemuApiType;
import com.badfisher.temu.business.order.enums.TemuOrderStatus;
import com.badfisher.temu.client.exception.TemuApiException;
import com.badfisher.temu.business.sync.support.RetryHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TemuOrderPullService {

	@Resource
	private TemuApiClient temuApiClient;

	@Resource
	private SyncTaskService temuOrderRequestTaskService;

	@Resource
	private TemuParentOrderService temuParentOrderService;

	@Resource
	private TemuChildrenOrderService temuChildrenOrderService;

	@Resource
	private TemuOrderAddressService temuOrderAddressService;

	@Resource
	private TemuOrderProductService temuOrderProductService;

	@Resource
	private TemuOrderAmountPullService temuOrderAmountPullService;

	@Resource
	private TemuGlobalAccountResolver temuGlobalAccountResolver;

	@Resource
	private PlatformTransactionManager transactionManager;

	private TransactionTemplate transactionTemplate;

	@PostConstruct
	private void initTransactionTemplate() {
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private static final int PAGE_SIZE = 100;

	/** Temu订单列表单个查询窗口允许返回的最大记录数。 */
	private static final int MAX_SEARCH_RESULT_COUNT = 10_000;

	/** 翻页硬上限，防止异常情况下无限翻页 */
	private static final int MAX_PAGE_CAP = 1000;

	/** 连续失败上限，超过即终止翻页 */
	private static final int MAX_CONSECUTIVE_FAILURES = 3;

	public void doPullTask(TemuAccessToken account, SyncTask task, TemuAuthRegion authRegion) {
		String sellerId = task.getAccountCode();

		try {
			temuOrderRequestTaskService.checkExecution(task);
			log.info("开始拉取订单: accountCode={}, beginTime={}, endTime={}", sellerId, task.getBeginTime(),
					task.getEndTime());

			Map<Integer, TemuAccessToken> siteAccountMap = isGlobalRegion(authRegion)
					? temuGlobalAccountResolver.buildSiteAccountMap(account) : null;
			int totalProcessed = pullTaskWindow(task, account, authRegion, siteAccountMap);

			log.info("店铺 {} 订单拉取完成: 处理订单数={}", sellerId, totalProcessed);
			markTaskComplete(task);

		}
		catch (TaskLeaseLostException e) {
			throw e;
		}
		catch (TemuApiException e) {
			log.error("店铺 {} 订单拉取失败: code={}, reason={}, msg={}, apiUrl={}", sellerId, e.getErrorCode(),
					e.getErrorCodeEnum() != null ? e.getErrorCodeEnum().getChnDesc() : "未知", e.getMessage(),
					e.getApiUrl());
			if (e.isTokenExpired()) {
				log.warn("店铺 {} accessToken 已过期，需提醒卖家重新授权", sellerId);
			}
			markTaskFailed(task, e.getMessage());
		}
		catch (Exception e) {
			log.error("店铺 {} 订单拉取失败: error={}", sellerId, e.getMessage(), e);
			markTaskFailed(task, e.getMessage());
		}
	}

	/**
	 * 拉取任务时间窗。首次全量窗口过大时，根据第一页总数或平台21008错误自动二分， 所有子窗口均成功后才允许任务完成；后续小窗口增量直接执行一次。
	 */
	private int pullTaskWindow(SyncTask task, TemuAccessToken account, TemuAuthRegion authRegion,
			Map<Integer, TemuAccessToken> siteAccountMap) {
		if (task.getBeginTime() == null || task.getEndTime() == null) {
			return pullAllPages(buildPullParams(task, null, null), account, authRegion, siteAccountMap, task);
		}
		long startSecond = task.getBeginTime().getTime() / 1000;
		long endSecond = task.getEndTime().getTime() / 1000;
		return pullOrderWindow(task, account, authRegion, siteAccountMap, startSecond, endSecond);
	}

	private int pullOrderWindow(SyncTask task, TemuAccessToken account, TemuAuthRegion authRegion,
			Map<Integer, TemuAccessToken> siteAccountMap, long startSecond, long endSecond) {
		try {
			return pullAllPages(buildPullParams(task, startSecond, endSecond), account, authRegion, siteAccountMap, task);
		}
		catch (SearchWindowTooLargeException e) {
			return splitAndPull(task, account, authRegion, siteAccountMap, startSecond, endSecond, "total=" + e.total);
		}
		catch (TemuApiException e) {
			if (!"21008".equals(e.getErrorCode())) {
				throw e;
			}
			return splitAndPull(task, account, authRegion, siteAccountMap, startSecond, endSecond,
					"Temu errorCode=21008");
		}
	}

	private int splitAndPull(SyncTask task, TemuAccessToken account, TemuAuthRegion authRegion,
			Map<Integer, TemuAccessToken> siteAccountMap, long startSecond, long endSecond, String reason) {
		if (startSecond >= endSecond) {
			throw new IllegalStateException("订单查询缩小到1秒仍超过平台上限: " + reason);
		}
		long middleSecond = startSecond + (endSecond - startSecond) / 2;
		log.warn("店铺 {} 订单查询结果超过平台上限，拆分窗口: [{} - {}] -> [{} - {}], [{} - {}], {}", account.getSellerId(), startSecond,
				endSecond, startSecond, middleSecond, middleSecond + 1, endSecond, reason);
		int leftCount = pullOrderWindow(task, account, authRegion, siteAccountMap, startSecond, middleSecond);
		int rightCount = pullOrderWindow(task, account, authRegion, siteAccountMap, middleSecond + 1, endSecond);
		return leftCount + rightCount;
	}

	@SuppressWarnings("unchecked")
	private int pullAllPages(Map<String, Object> baseParams, TemuAccessToken account, TemuAuthRegion authRegion,
			Map<Integer, TemuAccessToken> siteAccountMap, SyncTask task) {
		String sellerId = account.getSellerId();
		int totalProcessed = 0;
		int maxPage = MAX_PAGE_CAP;
		int consecutiveFailures = 0;
		int totalFailedPages = 0;

		for (int page = 1; page <= maxPage; page++) {
			PageResult pr = fetchOrderPage(baseParams, page, account, authRegion, task);

			if (pr.isFailed()) {
				consecutiveFailures++;
				totalFailedPages++;
				if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
					log.error("店铺 {} 连续 {} 页拉取失败, 终止翻页", sellerId, consecutiveFailures);
					break;
				}
				continue;
			}
			consecutiveFailures = 0;

			if (pr.isEmpty()) {
				if (page == 1) {
					log.info("店铺 {} 当前时间窗口无订单数据", sellerId);
					return 0;
				}
				break;
			}

			// 写入第一页前先判断总量，避免翻到平台上限后再拆分并重复处理前面页面。
			if (page == 1 && pr.getTotal() > MAX_SEARCH_RESULT_COUNT) {
				throw new SearchWindowTooLargeException(pr.getTotal());
			}

			totalProcessed += processOrderPage(pr.getItems(), account, authRegion, siteAccountMap, task);

			if (pr.getTotal() > 0) {
				maxPage = Math.min(MAX_PAGE_CAP, (int) Math.ceil((double) pr.getTotal() / PAGE_SIZE));
			}
			if (pr.isLastPage()) {
				break;
			}
		}

		if (totalFailedPages > 0) {
			throw new RuntimeException(
					String.format("店铺 %s 有 %d 页拉取失败, 已成功 %d 条, 需重试", sellerId, totalFailedPages, totalProcessed));
		}

		return totalProcessed;
	}

	/**
	 * 拉取单页订单数据（含失败重试一次）。
	 * @return {@link PageResult} — 成功/空/失败三态
	 * @throws TemuApiException accessToken 过期时直接向上抛
	 */
	@SuppressWarnings("unchecked")
	private PageResult fetchOrderPage(Map<String, Object> baseParams, int page, TemuAccessToken account,
			TemuAuthRegion authRegion, SyncTask task) {
		String sellerId = account.getSellerId();
		Map<String, Object> response = RetryHelper.withRetry("店铺 " + sellerId + " 订单第 " + page + " 页", () -> {
			baseParams.put("pageNumber", page);
			temuOrderRequestTaskService.checkExecution(task);
			return temuApiClient.callByAuthRegion(authRegion, baseParams, account);
		});

		if (response == null) {
			return PageResult.failed();
		}

		Map<String, Object> result = (Map<String, Object>) response.get("result");
		if (result == null) {
			log.warn("店铺 {} 订单第 {} 页返回 result 为空", sellerId, page);
			return PageResult.empty();
		}

		List<Map<String, Object>> pageItems = (List<Map<String, Object>>) result.get("pageItems");
		if (pageItems == null || pageItems.isEmpty()) {
			return PageResult.empty();
		}

		int total = intVal(result.get("totalItemNum"));
		return PageResult.success(pageItems, total, PAGE_SIZE);
	}

	private Map<String, Object> buildPullParams(SyncTask task, Long startSecond, Long endSecond) {
		Map<String, Object> params = new HashMap<>();
		params.put("type", TemuApiType.ORDER_LIST_V2_GET.getType());
		params.put("pageNumber", 1);
		params.put("pageSize", PAGE_SIZE);

		if (startSecond != null) {
			params.put("updateAtStart", startSecond);
		}
		else if (task.getBeginTime() != null) {
			params.put("updateAtStart", task.getBeginTime().getTime() / 1000);
		}
		if (endSecond != null) {
			params.put("updateAtEnd", endSecond);
		}
		else if (task.getEndTime() != null) {
			params.put("updateAtEnd", task.getEndTime().getTime() / 1000);
		}

		if (task.getPullStatus() != null && !"0".equals(task.getPullStatus())) {
			params.put("parentOrderStatus", task.getPullStatus());
		}

		return params;
	}

	private static final class SearchWindowTooLargeException extends RuntimeException {

		private final int total;

		private SearchWindowTooLargeException(int total) {
			this.total = total;
		}

	}

	@SuppressWarnings("unchecked")
	private int processOrderPage(List<Map<String, Object>> pageItems, TemuAccessToken account,
			TemuAuthRegion authRegion, SyncTask task) {
		return processOrderPage(pageItems, account, authRegion, null, task);
	}

	@SuppressWarnings("unchecked")
	private int processOrderPage(List<Map<String, Object>> pageItems, TemuAccessToken account,
			TemuAuthRegion authRegion, Map<Integer, TemuAccessToken> siteAccountMap, SyncTask task) {
		String sellerId = account.getSellerId();
		if (pageItems == null || pageItems.isEmpty()) {
			return 0;
		}

		// Global页可混合多个站点，必须先逐单解析真实ERP店铺再查询和落库。
		List<String> parentOrderSns = new ArrayList<>();
		Map<String, TemuAccessToken> targetAccountByParentSn = new LinkedHashMap<>();
		for (Map<String, Object> orderMap : pageItems) {
			Map<String, Object> parentOrderMap = (Map<String, Object>) orderMap.get("parentOrderMap");
			if (parentOrderMap == null) {
				log.warn("pageItem 缺少 parentOrderMap: {}", orderMap);
				continue;
			}
			String parentOrderSn = str(parentOrderMap.get("parentOrderSn"));
			if (parentOrderSn == null) {
				throw new IllegalStateException("Global订单缺少parentOrderSn: " + parentOrderMap.keySet());
			}
			parentOrderSns.add(parentOrderSn);
			TemuAccessToken targetAccount = siteAccountMap == null ? account : temuGlobalAccountResolver
				.resolve(intVal(parentOrderMap.get("siteId")), siteAccountMap, account, parentOrderSn);
			targetAccountByParentSn.put(parentOrderSn, targetAccount);
		}
		if (parentOrderSns.isEmpty()) {
			return 0;
		}

		Set<Integer> targetChannelIds = targetAccountByParentSn.values()
			.stream()
			.map(TemuAccessToken::getChannelId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
		boolean includeUnmappedChannel = targetAccountByParentSn.values()
			.stream()
			.anyMatch(target -> target.getChannelId() == null);
		// 批量查询页内所有目标店铺的已存在数据，避免循环内 N+1 查询。
		Map<String, TemuParentOrder> existParentMap = temuParentOrderService
			.list(Wrappers.lambdaQuery(TemuParentOrder.class).eq(TemuParentOrder::getSellerId, sellerId).and(query -> {
				if (includeUnmappedChannel) {
					query.isNull(TemuParentOrder::getChannelId);
					if (!targetChannelIds.isEmpty()) {
						query.or().in(TemuParentOrder::getChannelId, targetChannelIds);
					}
				}
				else {
					query.in(TemuParentOrder::getChannelId, targetChannelIds);
				}
			}).in(TemuParentOrder::getParentOrderSn, parentOrderSns))
			.stream()
			.collect(Collectors.toMap(p -> ownershipKey(p.getChannelId(), p.getSite(), p.getParentOrderSn()), p -> p,
					(a, b) -> a));

		Map<String, TemuOrderAddress> existAddressMap = temuOrderAddressService.list(
				Wrappers.lambdaQuery(TemuOrderAddress.class).eq(TemuOrderAddress::getSellerId, sellerId).and(query -> {
					if (includeUnmappedChannel) {
						query.isNull(TemuOrderAddress::getChannelId);
						if (!targetChannelIds.isEmpty()) {
							query.or().in(TemuOrderAddress::getChannelId, targetChannelIds);
						}
					}
					else {
						query.in(TemuOrderAddress::getChannelId, targetChannelIds);
					}
				}).in(TemuOrderAddress::getParentOrderSn, parentOrderSns))
			.stream()
			.collect(Collectors.toMap(a -> ownershipKey(a.getChannelId(), a.getSite(), a.getParentOrderSn()), a -> a,
					(a, b) -> a));

		Map<String, List<TemuChildrenOrder>> existChildrenBySn = temuChildrenOrderService
			.list(Wrappers.lambdaQuery(TemuChildrenOrder.class)
				.eq(TemuChildrenOrder::getSellerId, sellerId)
				.and(query -> {
					if (includeUnmappedChannel) {
						query.isNull(TemuChildrenOrder::getChannelId);
						if (!targetChannelIds.isEmpty()) {
							query.or().in(TemuChildrenOrder::getChannelId, targetChannelIds);
						}
					}
					else {
						query.in(TemuChildrenOrder::getChannelId, targetChannelIds);
					}
				})
				.in(TemuChildrenOrder::getParentOrderSn, parentOrderSns))
			.stream()
			.collect(Collectors.groupingBy(c -> ownershipKey(c.getChannelId(), c.getSite(), c.getParentOrderSn())));

		Map<String, List<TemuOrderProduct>> existProductBySn = temuOrderProductService.list(
				Wrappers.lambdaQuery(TemuOrderProduct.class).eq(TemuOrderProduct::getSellerId, sellerId).and(query -> {
					if (includeUnmappedChannel) {
						query.isNull(TemuOrderProduct::getChannelId);
						if (!targetChannelIds.isEmpty()) {
							query.or().in(TemuOrderProduct::getChannelId, targetChannelIds);
						}
					}
					else {
						query.in(TemuOrderProduct::getChannelId, targetChannelIds);
					}
				}).in(TemuOrderProduct::getParentOrderSn, parentOrderSns))
			.stream()
			.collect(Collectors.groupingBy(p -> ownershipKey(p.getChannelId(), p.getSite(), p.getParentOrderSn())));

		int processed = 0;
		List<TemuParentOrder> parentOrders = new ArrayList<>();
		List<TemuChildrenOrder> pageChildrenOrders = new ArrayList<>();
		List<TemuOrderProduct> pageProducts = new ArrayList<>();
		List<Long> pageChildIdsToRemove = new ArrayList<>();
		List<Long> pageProductIdsToRemove = new ArrayList<>();
		for (Map<String, Object> orderMap : pageItems) {
			try {
				Map<String, Object> parentOrderMap = (Map<String, Object>) orderMap.get("parentOrderMap");
				if (parentOrderMap == null) {
					continue;
				}
				String parentOrderSn = str(parentOrderMap.get("parentOrderSn"));
				if (parentOrderSn == null) {
					continue;
				}

				TemuAccessToken targetAccount = targetAccountByParentSn.get(parentOrderSn);
				Integer channelId = targetAccount.getChannelId();
				String siteCode = targetAccount.getSite();
				String parentOwnershipKey = ownershipKey(channelId, siteCode, parentOrderSn);

				TemuParentOrder parentOrder = buildParentOrder(parentOrderMap, parentOrderSn, sellerId, channelId,
						siteCode);
				TemuParentOrder exist = existParentMap.get(parentOwnershipKey);
				if (exist != null) {
					parentOrder.setId(exist.getId());
					parentOrder.setCreateTime(exist.getCreateTime());
				}
				else {
					parentOrder.setCreateTime(new Date());
				}
				List<Map<String, Object>> orderList = (List<Map<String, Object>>) orderMap.get("orderList");
				List<TemuChildrenOrder> childrenOrders = new ArrayList<>();
				List<TemuOrderProduct> products = new ArrayList<>();
				List<Long> childIdsToRemove = new ArrayList<>();
				List<Long> productIdsToRemove = new ArrayList<>();
				if (orderList != null && !orderList.isEmpty()) {
					Map<String, TemuChildrenOrder> existChildrenMap = existChildrenBySn
						.getOrDefault(parentOwnershipKey, Collections.<TemuChildrenOrder>emptyList())
						.stream()
						.collect(Collectors.toMap(TemuChildrenOrder::getOrderSn, c -> c, (a, b) -> a));

					Map<String, TemuOrderProduct> existProductMap = existProductBySn
						.getOrDefault(parentOwnershipKey, Collections.<TemuOrderProduct>emptyList())
						.stream()
						.collect(Collectors.toMap(p -> p.getChildrenOrderNo() + "_" + p.getProductSkuId(), p -> p,
								(a, b) -> a));

					// 收集本次 API 返回的子单号与货品键，用于删除已不存在的旧记录
					Set<String> currentChildSns = new HashSet<>();
					Set<String> currentProductKeys = new HashSet<>();
					Date now = new Date();

					for (Map<String, Object> itemMap : orderList) {
						TemuChildrenOrder child = buildChildrenOrder(itemMap, parentOrderSn, sellerId, channelId,
								siteCode, now);
						TemuChildrenOrder existChild = existChildrenMap.get(child.getOrderSn());
						if (existChild != null) {
							child.setId(existChild.getId());
							child.setCreateTime(existChild.getCreateTime());
						}
						childrenOrders.add(child);
						currentChildSns.add(child.getOrderSn());

						List<Map<String, Object>> productList = (List<Map<String, Object>>) itemMap.get("productList");
						if (productList != null) {
							for (Map<String, Object> prodMap : productList) {
								TemuOrderProduct product = buildProduct(prodMap, parentOrderSn, child.getOrderSn(),
										sellerId, channelId, siteCode, now);
								TemuOrderProduct existProd = existProductMap
									.get(product.getChildrenOrderNo() + "_" + product.getProductSkuId());
								if (existProd != null) {
									product.setId(existProd.getId());
								}
								products.add(product);
								currentProductKeys.add(product.getChildrenOrderNo() + "_" + product.getProductSkuId());
							}
						}
					}

					// 计算需删除的旧记录：DB 有但本次 API 未返回的子单/货品
					for (TemuChildrenOrder existChild : existChildrenMap.values()) {
						if (!currentChildSns.contains(existChild.getOrderSn())) {
							childIdsToRemove.add(existChild.getId());
						}
					}
					for (TemuOrderProduct existProd : existProductMap.values()) {
						String key = existProd.getChildrenOrderNo() + "_" + existProd.getProductSkuId();
						if (!currentProductKeys.contains(key)) {
							productIdsToRemove.add(existProd.getId());
						}
					}

				}

				// 单条订单解析完整后再并入页级集合，避免解析异常留下半条内存数据。
				parentOrders.add(parentOrder);
				pageChildrenOrders.addAll(childrenOrders);
				pageProducts.addAll(products);
				pageChildIdsToRemove.addAll(childIdsToRemove);
				pageProductIdsToRemove.addAll(productIdsToRemove);
				// 金额拉取由独立 temu*AmountPullJob 任务按 task 窗口拉取
				// temu-api金额拉取接口已经取消 无法拉取
				// temuOrderAmountPullService.pullAmount(
				// parentOrderSn, sellerId, appKey, appSecret, accessToken, site);
				processed++;

			}
			catch (Exception e) {
				throw new IllegalStateException("处理Global订单失败: sellerId=" + sellerId, e);
			}
		}

		// 本页父单、子单和货品共用一个事务；任一写入失败则整页回滚。
		if (!parentOrders.isEmpty()) {
			transactionTemplate.executeWithoutResult(status -> {
				temuOrderRequestTaskService.lockExecution(task);
				// 父单必须先落库，保证任何已提交的子单和货品都能找到父记录。
				temuParentOrderService.insertOrUpdateBatch(parentOrders);
				if (!pageProductIdsToRemove.isEmpty()) {
					temuOrderProductService.removeByIds(pageProductIdsToRemove);
				}
				if (!pageChildIdsToRemove.isEmpty()) {
					temuChildrenOrderService.removeByIds(pageChildIdsToRemove);
				}
				if (!pageChildrenOrders.isEmpty()) {
					temuChildrenOrderService.insertOrUpdateBatch(pageChildrenOrders);
				}
				if (!pageProducts.isEmpty()) {
					temuOrderProductService.insertOrUpdateBatch(pageProducts);
				}
			});
		}

		// 地址接口属于外部IO，放在基础订单事务提交后执行，避免长事务并确保父单已存在。
		for (TemuParentOrder parentOrder : parentOrders) {
			String parentOrderSn = parentOrder.getParentOrderSn();
			if (isUnShipping(parentOrder.getParentOrderStatus())) {
				TemuAccessToken targetAccount = targetAccountByParentSn.get(parentOrderSn);
				handleAddress(parentOrderSn,
						existAddressMap
							.get(ownershipKey(targetAccount.getChannelId(), targetAccount.getSite(), parentOrderSn)),
						account, targetAccount, authRegion, task);
			}
			else {
				log.debug("订单 {} 状态为 {}，跳过地址拉取", parentOrderSn, parentOrder.getParentOrderStatus());
			}
		}

		return processed;
	}

	private boolean isGlobalRegion(TemuAuthRegion authRegion) {
		return authRegion == TemuAuthRegion.GLOBAL || authRegion == TemuAuthRegion.EU;
	}

	private static String ownershipKey(Integer channelId, String site, String businessSn) {
		return channelId + "|" + site + "|" + businessSn;
	}

	private TemuParentOrder buildParentOrder(Map<String, Object> map, String parentOrderSn, String sellerId,
			Integer channelId, String site) {
		TemuParentOrder order = new TemuParentOrder();
		order.setParentOrderSn(parentOrderSn);
		order.setParentOrderStatus(str(map.get("parentOrderStatus")));
		order.setSellerId(sellerId);
		order.setChannelId(channelId);
		order.setSite(site);

		order.setParentOrderTime(longVal(map.get("parentOrderTime")));
		order.setParentShippingTime(longVal(map.get("parentShippingTime")));
		order.setExpectShipLatestTime(longVal(map.get("expectShipLatestTime")));
		order.setLatestDeliveryTime(longVal(map.get("latestDeliveryTime")));
		order.setParentConfirmTime(longVal(map.get("parentConfirmTime")));
		order.setParentOrderPendingFinishTime(longVal(map.get("parentOrderPendingFinishTime")));
		order.setUpdateTimeApi(longVal(map.get("updateTime")));

		order.setShippingMethod(intVal(map.get("shippingMethod")));
		order.setOrderPaymentType(str(map.get("orderPaymentType")));
		order.setIsShipmentConsolidatedByMainMall(boolVal(map.get("isShipmentConsolidatedByMainMall")));
		order.setHasShippingFee(boolVal(map.get("hasShippingFee")));

		order.setRegionId(intVal(map.get("regionId")));
		order.setSiteId(intVal(map.get("siteId")));

		order.setDelFlag(0);
		order.setUpdateTime(new Date());
		return order;
	}

	private TemuChildrenOrder buildChildrenOrder(Map<String, Object> map, String parentOrderSn, String sellerId,
			Integer channelId, String site, Date now) {
		TemuChildrenOrder child = new TemuChildrenOrder();
		child.setParentOrderSn(parentOrderSn);
		child.setSellerId(sellerId);
		child.setChannelId(channelId);
		child.setSite(site);
		child.setOrderSn(str(map.get("orderSn")));
		child.setGoodsName(str(map.get("goodsName")));
		child.setSpec(str(map.get("spec")));
		child.setThumbUrl(str(map.get("thumbUrl")));

		Object goodsId = map.get("goodsId");
		if (goodsId != null) {
			child.setGoodsId(Long.parseLong(String.valueOf(goodsId)));
		}
		Object orderStatus = map.get("orderStatus");
		if (orderStatus != null) {
			child.setOrderStatus(Integer.parseInt(String.valueOf(orderStatus)));
		}
		Object quantity = map.get("quantity");
		if (quantity != null) {
			child.setQuantity(Integer.parseInt(String.valueOf(quantity)));
		}
		Object skuId = map.get("skuId");
		if (skuId != null) {
			child.setSkuId(Long.parseLong(String.valueOf(skuId)));
		}

		child.setUpdateTime(now);
		child.setDelFlag(0);
		return child;
	}

	private TemuOrderProduct buildProduct(Map<String, Object> map, String parentOrderSn, String childrenOrderNo,
			String sellerId, Integer channelId, String site, Date now) {
		TemuOrderProduct product = new TemuOrderProduct();
		product.setParentOrderSn(parentOrderSn);
		product.setSellerId(sellerId);
		product.setChannelId(channelId);
		product.setSite(site);
		product.setChildrenOrderNo(childrenOrderNo);
		product.setExtCode(str(map.get("extCode")));
		product.setSoldFactor(str(map.get("soldFactor")));

		Object productId = map.get("productId");
		if (productId != null) {
			product.setProductId(Long.parseLong(String.valueOf(productId)));
		}
		Object productSkuId = map.get("productSkuId");
		if (productSkuId != null) {
			product.setProductSkuId(Long.parseLong(String.valueOf(productSkuId)));
		}

		product.setUpdateTime(now);
		product.setDelFlag(0);
		return product;
	}

	@SuppressWarnings("unchecked")
	private void handleAddress(String parentOrderSn, TemuOrderAddress backupAddress, TemuAccessToken requestAccount,
			TemuAccessToken targetAccount, TemuAuthRegion authRegion, SyncTask task) {
		String sellerId = targetAccount.getSellerId();
		Integer channelId = targetAccount.getChannelId();
		String siteCode = targetAccount.getSite();
		try {
			Map<String, Object> params = new HashMap<>();
			params.put("type", TemuApiType.ORDER_SHIPPINGINFO_V2_GET.getType());
			params.put("parentOrderSn", parentOrderSn);

			if (task != null) {
				temuOrderRequestTaskService.checkExecution(task);
			}
			Map<String, Object> response = temuApiClient.callByAuthRegion(authRegion, params, requestAccount);
			Map<String, Object> addressData = (Map<String, Object>) response.get("result");
			if (addressData == null || addressData.get("receiptName") == null) {
				log.warn("订单 {} 地址接口返回无收货人, 使用备份地址", parentOrderSn);
				fallbackToBackup(parentOrderSn, sellerId, channelId, siteCode, backupAddress, task);
				return;
			}

			TemuOrderAddress address = new TemuOrderAddress();
			address.setParentOrderSn(parentOrderSn);
			address.setSellerId(sellerId);
			address.setChannelId(channelId);
			address.setSite(siteCode);
			address.setReceiptName(str(addressData.get("receiptName")));
			address.setMobile(str(addressData.get("mobile")));
			address.setMail(str(addressData.get("mail")));
			address.setRegionname1(str(addressData.get("regionName1")));
			address.setRegionname2(str(addressData.get("regionName2")));
			address.setRegionname3(str(addressData.get("regionName3")));
			address.setAddressline1(str(addressData.get("addressLine1")));
			address.setAddressline2(str(addressData.get("addressLine2")));
			address.setPostCode(str(addressData.get("postCode")));

			if (backupAddress != null && backupAddress.getId() != null) {
				address.setId(backupAddress.getId());
			}
			String buyerName = address.getReceiptName();
			final TemuOrderAddress addr = address;
			transactionTemplate.executeWithoutResult(status -> {
				if (task != null) {
					temuOrderRequestTaskService.lockExecution(task);
				}
				temuOrderAddressService.saveOrUpdate(addr);
				temuParentOrderService.update(Wrappers.lambdaUpdate(TemuParentOrder.class)
					.set(TemuParentOrder::getBuyerName, buyerName)
					.set(TemuParentOrder::getUpdateTime, new Date())
					.eq(TemuParentOrder::getParentOrderSn, parentOrderSn)
					.eq(TemuParentOrder::getSellerId, sellerId)
					.eq(channelId != null, TemuParentOrder::getChannelId, channelId)
					.isNull(channelId == null, TemuParentOrder::getChannelId)
					.eq(TemuParentOrder::getSite, siteCode));
			});

		}
		catch (TaskLeaseLostException e) {
			throw e;
		}
		catch (Exception e) {
			log.warn("获取订单 {} 地址信息失败: {}, 使用备份地址", parentOrderSn, e.getMessage());
			fallbackToBackup(parentOrderSn, sellerId, channelId, siteCode, backupAddress, task);
		}
	}

	private void fallbackToBackup(String parentOrderSn, String sellerId, Integer channelId, String site,
			TemuOrderAddress backupAddress, SyncTask task) {
		if (backupAddress != null && backupAddress.getReceiptName() != null) {
			String buyerName = backupAddress.getReceiptName();
			transactionTemplate.executeWithoutResult(status -> {
				if (task != null) {
					temuOrderRequestTaskService.lockExecution(task);
				}
				temuParentOrderService.update(Wrappers.lambdaUpdate(TemuParentOrder.class)
					.set(TemuParentOrder::getBuyerName, buyerName)
					.set(TemuParentOrder::getUpdateTime, new Date())
					.eq(TemuParentOrder::getParentOrderSn, parentOrderSn)
					.eq(TemuParentOrder::getSellerId, sellerId)
					.eq(channelId != null, TemuParentOrder::getChannelId, channelId)
					.isNull(channelId == null, TemuParentOrder::getChannelId)
					.eq(TemuParentOrder::getSite, site));
			});
		}
	}

	/**
	 * 补救拉取指定订单的地址信息 — 用于批量修复历史订单缺失的地址数据。
	 */
	@SuppressWarnings("unchecked")
	public void remediateAddress(String parentOrderSn, String sellerId, TemuAccessToken account,
			TemuAuthRegion authRegion) {
		TemuParentOrder parentOrder = temuParentOrderService.getOne(Wrappers.lambdaQuery(TemuParentOrder.class)
			.eq(TemuParentOrder::getParentOrderSn, parentOrderSn)
			.eq(TemuParentOrder::getSellerId, sellerId)
			.eq(TemuParentOrder::getChannelId, account.getChannelId())
			.eq(TemuParentOrder::getSite, account.getSite()));
		if (parentOrder == null) {
			log.warn("补救地址失败: 父订单不存在 sellerId={}, parentOrderSn={}", sellerId, parentOrderSn);
			return;
		}
		if (!isUnShipping(parentOrder.getParentOrderStatus())) {
			log.info("跳过地址补偿: sellerId={}, parentOrderSn={}, status={}，仅未发货订单拉取地址", sellerId, parentOrderSn,
					parentOrder.getParentOrderStatus());
			return;
		}

		TemuOrderAddress backupAddress = temuOrderAddressService.getOne(Wrappers.lambdaQuery(TemuOrderAddress.class)
			.eq(TemuOrderAddress::getParentOrderSn, parentOrderSn)
			.eq(TemuOrderAddress::getSellerId, sellerId)
			.eq(TemuOrderAddress::getChannelId, account.getChannelId())
			.eq(TemuOrderAddress::getSite, account.getSite()));

		// 独立地址补救入口不使用任务表；任务链必须显式传入领取后的任务。
		handleAddress(parentOrderSn, backupAddress, account, account, authRegion, null);
	}

	private static String str(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	/** 兼容API及历史数据中的数字状态"2"和英文状态"UN_SHIPPING"。 */
	private static boolean isUnShipping(String status) {
		if (status == null) {
			return false;
		}
		String value = status.trim();
		return String.valueOf(TemuOrderStatus.UN_SHIPPING.getCode()).equals(value)
				|| TemuOrderStatus.UN_SHIPPING.getEngName().equalsIgnoreCase(value);
	}

	private static Long longVal(Object value) {
		if (value == null)
			return null;
		try {
			return Long.parseLong(String.valueOf(value));
		}
		catch (Exception e) {
			return null;
		}
	}

	private static Integer intVal(Object value) {
		if (value == null)
			return null;
		try {
			return Integer.parseInt(String.valueOf(value));
		}
		catch (Exception e) {
			return null;
		}
	}

	/** JSON 布尔 / 字符串 "true" → 1，否则 0；null 保持 null */
	private static Integer boolVal(Object value) {
		if (value == null)
			return null;
		if (value instanceof Boolean) {
			return ((Boolean) value) ? 1 : 0;
		}
		return "true".equalsIgnoreCase(String.valueOf(value)) ? 1 : 0;
	}

	/** 将 List 序列化为 JSON 数组字符串；null 或空数组返回 null */
	private static String toJsonArray(Object value) {
		if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
			return null;
		}
		try {
			return OBJECT_MAPPER.writeValueAsString(value);
		}
		catch (Exception e) {
			log.warn("序列化 fulfillmentWarning 失败: {}", e.getMessage());
			return null;
		}
	}

	private void markTaskComplete(SyncTask task) {
		if (!temuOrderRequestTaskService.completeTask(task.getId(), task.getExecutionId())) {
			throw new TaskLeaseLostException("任务完成回写被拒绝: taskId=" + task.getId());
		}
	}

	private void markTaskFailed(SyncTask task, String errorMessage) {
		if (!temuOrderRequestTaskService.failTask(task.getId(), task.getExecutionId(), errorMessage)) {
			log.warn("订单任务 {} 失败状态回写被忽略，任务可能已被超时回收", task.getId());
		}
	}

}
