package com.badfisher.temu.business.aftersales.service;

import com.badfisher.temu.business.account.service.TemuGlobalAccountResolver;
import com.badfisher.temu.business.order.service.TemuParentOrderService;
import com.badfisher.temu.business.sync.service.SyncTaskService;
import com.badfisher.temu.business.sync.execution.TaskLeaseLostException;

import com.badfisher.temu.client.TemuApiClient;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.aftersales.model.TemuAftersalesProduct;
import com.badfisher.temu.business.aftersales.model.TemuAftersalesReturnLogistics;
import com.badfisher.temu.business.aftersales.model.TemuChildrenAftersales;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.aftersales.model.TemuParentAftersales;
import com.badfisher.temu.business.order.model.TemuParentOrder;
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
import java.util.stream.Collectors;

/**
 * Temu 售后单拉取服务 — 拉取售后主单、详情、商品和退货物流并落库
 * <p>
 * 拉取流程： <pre>
 *   Step 1: bg.aftersales.parentaftersales.list.get
 *           └─ 按 task 时间窗口分页拉取父售后单 → 落库 tb_temu_parent_aftersales
 *           └─ 收集所有 parentAfterSalesSn
 *
 *   Step 2: bg.aftersales.aftersales.list.get
 *           └─ 按 parentAfterSalesSnList 分批拉取子售后单 → 落库 tb_temu_aftersales
 *           └─ 解析子售后单的 productList → 落库 tb_temu_aftersales_product
 *
 *   Step 3: temu.aftersales.parentaftersales.detail.get
 *           └─ 补齐售后原因、买家说明、子订单号和详情商品
 *
 *   Step 4: bg.aftersales.parentreturnorder.get
 *           └─ 对进入退货阶段的退货退款单拉取物流
 *              → 落库 tb_temu_aftersales_return_logistics
 * </pre>
 *
 * 事务策略与订单一致：API 调用在事务外，使用 TransactionTemplate 短事务落库。
 *
 * @author lenkor
 */
@Slf4j
@Service
public class TemuAftersalesPullService {

	@Resource
	private TemuApiClient temuApiClient;

	@Resource
	private SyncTaskService temuOrderRequestTaskService;

	@Resource
	private TemuParentAftersalesService temuParentAftersalesService;

	@Resource
	private TemuParentOrderService temuParentOrderService;

	@Resource
	private TemuGlobalAccountResolver temuGlobalAccountResolver;

	@Resource
	private TemuChildrenAftersalesService temuChildrenAftersalesService;

	@Resource
	private TemuAftersalesProductService temuAftersalesProductService;

	@Resource
	private TemuAftersalesReturnLogisticsService temuAftersalesReturnLogisticsService;

	@Resource
	private PlatformTransactionManager transactionManager;

	private TransactionTemplate transactionTemplate;

	@PostConstruct
	private void initTransactionTemplate() {
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	private static final int PAGE_SIZE = 100;

	private static final int PARENT_SN_BATCH_SIZE = 50;

	private static final int AFTERSALES_TYPE_RETURN_AND_REFUND = 2;

	/** 翻页硬上限，防止异常情况下无限翻页 */
	private static final int MAX_PAGE_CAP = 1000;

	/** 连续失败上限，超过即终止翻页 */
	private static final int MAX_CONSECUTIVE_FAILURES = 3;

	// ==================== 主流程 ====================

	@SuppressWarnings("unchecked")
	public void doPullTask(TemuAccessToken account, SyncTask task, TemuAuthRegion authRegion) {
		String sellerId = task.getAccountCode();

		try {
			temuOrderRequestTaskService.checkExecution(task);
			log.info("开始拉取售后: accountCode={}, beginTime={}, endTime={}", sellerId, task.getBeginTime(),
					task.getEndTime());

			Map<Integer, TemuAccessToken> siteAccountMap = isGlobalRegion(authRegion)
					? temuGlobalAccountResolver.buildSiteAccountMap(account) : null;
			// Step 1: 拉取父售后
			List<TemuParentAftersales> parentAftersales = pullParentAftersales(account, task, authRegion,
					siteAccountMap);

			if (parentAftersales.isEmpty()) {
				log.info("店铺 {} 当前时间窗口无父售后单数据", sellerId);
				markTaskComplete(task);
				return;
			}

			List<String> parentAftersalesSns = parentAftersales.stream()
				.map(TemuParentAftersales::getParentAftersalesSn)
				.filter(Objects::nonNull)
				.distinct()
				.collect(Collectors.toList());
			Map<String, TemuAccessToken> ownershipByParentAftersalesSn = buildAftersalesOwnership(parentAftersales,
					account, siteAccountMap);

			// Step 2: 按父售后单号列表分批拉取子售后单 + 产品
			int childProcessed = pullChildAftersales(account, authRegion, parentAftersalesSns,
					ownershipByParentAftersalesSn, task);

			// Step 3: 逐父售后单补齐售后原因、买家留言、子订单号和详情商品。
			int detailProcessed = pullParentAftersalesDetails(account, authRegion, parentAftersales,
					ownershipByParentAftersalesSn, task);

			// Step 4: 仅对已经进入退货物流阶段的退货退款单查询并替换当前物流。
			int logisticsProcessed = pullReturnLogistics(account, authRegion, parentAftersales,
					ownershipByParentAftersalesSn, task);

			log.info("店铺 {} 售后单拉取完成: 父售后单数={}, 子售后单数={}, 详情数={}, 退货物流数={}", sellerId, parentAftersalesSns.size(),
					childProcessed, detailProcessed, logisticsProcessed);
			markTaskComplete(task);

		}
		catch (TaskLeaseLostException e) {
			throw e;
		}
		catch (TemuApiException e) {
			log.error("店铺 {} 售后单拉取失败: code={}, reason={}, msg={}, apiUrl={}", sellerId, e.getErrorCode(),
					e.getErrorCodeEnum() != null ? e.getErrorCodeEnum().getChnDesc() : "未知", e.getMessage(),
					e.getApiUrl());
			if (e.isTokenExpired()) {
				log.warn("店铺 {} accessToken 已过期，需提醒卖家重新授权", sellerId);
			}
			markTaskFailed(task, e.getMessage());
		}
		catch (Exception e) {
			log.error("店铺 {} 售后单拉取失败: error={}", sellerId, e.getMessage(), e);
			markTaskFailed(task, e.getMessage());
		}
	}

	// ==================== Step 1: 拉取父售后单 ====================

	@SuppressWarnings("unchecked")
	private List<TemuParentAftersales> pullParentAftersales(TemuAccessToken account, SyncTask task,
			TemuAuthRegion authRegion, Map<Integer, TemuAccessToken> siteAccountMap) {
		String sellerId = account.getSellerId();
		Map<String, TemuParentAftersales> allParents = new LinkedHashMap<>();
		int totalProcessed = 0;
		int consecutiveFailures = 0;
		int totalFailedPages = 0;

		Map<String, Object> params = buildParentPullParams(task);
		int maxPage = MAX_PAGE_CAP;

		for (int page = 1; page <= maxPage; page++) {
			PageResult pr = fetchParentPage(params, page, account, authRegion, task);

			if (pr.isFailed()) {
				consecutiveFailures++;
				totalFailedPages++;
				if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
					log.error("店铺 {} 父售后单连续 {} 页拉取失败, 终止翻页", sellerId, consecutiveFailures);
					break;
				}
				continue;
			}
			consecutiveFailures = 0;

			if (pr.isEmpty()) {
				if (page == 1) {
					log.info("店铺 {} 当前时间窗口无父售后单数据", sellerId);
				}
				break;
			}

			List<TemuParentAftersales> pageParents = processParentAftersalesPage(pr.getItems(), account,
					siteAccountMap, task);
			totalProcessed += pageParents.size();
			for (TemuParentAftersales parent : pageParents) {
				allParents.put(parent.getParentAftersalesSn(), parent);
			}

			if (pr.getTotal() > 0) {
				maxPage = Math.min(MAX_PAGE_CAP, (int) Math.ceil((double) pr.getTotal() / PAGE_SIZE));
			}
			if (pr.isLastPage()) {
				break;
			}
		}

		if (totalFailedPages > 0) {
			throw new RuntimeException(
					String.format("店铺 %s 父售后单有 %d 页拉取失败, 已成功 %d 条, 需重试", sellerId, totalFailedPages, totalProcessed));
		}

		log.info("店铺 {} 父售后单拉取完成: 共 {} 条", sellerId, totalProcessed);
		return new ArrayList<>(allParents.values());
	}

	/**
	 * 拉取单页父售后单数据（含失败重试一次）。
	 * @return {@link PageResult} — 成功/空/失败三态
	 * @throws TemuApiException accessToken 过期时直接向上抛
	 */
	@SuppressWarnings("unchecked")
	private PageResult fetchParentPage(Map<String, Object> params, int page, TemuAccessToken account,
			TemuAuthRegion authRegion, SyncTask task) {
		String sellerId = account.getSellerId();
		Map<String, Object> response = RetryHelper.withRetry("店铺 " + sellerId + " 父售后单第 " + page + " 页", () -> {
			params.put("pageNumber", page);
			temuOrderRequestTaskService.checkExecution(task);
			return temuApiClient.callByAuthRegion(authRegion, params, account);
		});

		if (response == null) {
			return PageResult.failed();
		}

		Map<String, Object> result = (Map<String, Object>) response.get("result");
		if (result == null) {
			log.warn("店铺 {} 父售后单第 {} 页返回 result 为空", sellerId, page);
			return PageResult.empty();
		}

		List<Map<String, Object>> items = extractDataList(result);
		if (items == null || items.isEmpty()) {
			return PageResult.empty();
		}

		return PageResult.success(items, intVal(result.get("total")), PAGE_SIZE);
	}

	private Map<String, Object> buildParentPullParams(SyncTask task) {
		Map<String, Object> params = new HashMap<>();
		params.put("type", TemuApiType.AFTERSALES_PARENT_LIST_GET.getType());
		params.put("pageSize", PAGE_SIZE);
		params.put("pageNumber", 1);

		if (task.getBeginTime() != null) {
			params.put("updateAtStart", task.getBeginTime().getTime() / 1000);
		}
		if (task.getEndTime() != null) {
			params.put("updateAtEnd", task.getEndTime().getTime() / 1000);
		}

		return params;
	}

	@SuppressWarnings("unchecked")
	private List<TemuParentAftersales> processParentAftersalesPage(List<Map<String, Object>> pageItems,
			TemuAccessToken account, Map<Integer, TemuAccessToken> siteAccountMap, SyncTask task) {
		String sellerId = account.getSellerId();
		if (pageItems == null || pageItems.isEmpty()) {
			return Collections.emptyList();
		}

		// 预收集父售后单号，批量查重
		List<String> sns = new ArrayList<>();
		List<String> parentOrderSns = new ArrayList<>();
		for (Map<String, Object> item : pageItems) {
			String sn = str(item.get("parentAfterSalesSn"));
			if (sn != null) {
				sns.add(sn);
			}
			String parentOrderSn = str(item.get("parentOrderSn"));
			if (parentOrderSn == null) {
				throw new IllegalStateException("父售后单缺少parentOrderSn: " + item.keySet());
			}
			parentOrderSns.add(parentOrderSn);
		}

		Map<String, TemuAccessToken> targetByParentOrderSn = new HashMap<>();
		if (siteAccountMap != null) {
			Set<Integer> channelIds = siteAccountMap.values()
				.stream()
				.map(TemuAccessToken::getChannelId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
			List<TemuParentOrder> orders = temuParentOrderService.list(Wrappers.lambdaQuery(TemuParentOrder.class)
				.eq(TemuParentOrder::getSellerId, sellerId)
				.and(query -> {
					query.isNull(TemuParentOrder::getChannelId);
					if (!channelIds.isEmpty()) {
						query.or().in(TemuParentOrder::getChannelId, channelIds);
					}
				})
				.in(TemuParentOrder::getParentOrderSn, parentOrderSns));
			for (TemuParentOrder order : orders) {
				TemuAccessToken target = temuGlobalAccountResolver.resolveBySite(order.getSite(), siteAccountMap,
						account, order.getParentOrderSn());
				if (!Objects.equals(target.getChannelId(), order.getChannelId())
						|| !Objects.equals(target.getSite(), order.getSite())) {
					throw new IllegalStateException("父订单归属与Global授权映射不一致: parentOrderSn=" + order.getParentOrderSn()
							+ ", channelId=" + order.getChannelId() + ", site=" + order.getSite());
				}
				if (targetByParentOrderSn.put(order.getParentOrderSn(), target) != null) {
					throw new IllegalStateException("父订单存在多条区域归属: parentOrderSn=" + order.getParentOrderSn());
				}
			}
			for (String parentOrderSn : parentOrderSns) {
				if (!targetByParentOrderSn.containsKey(parentOrderSn)) {
					throw new IllegalStateException(
							"Global售后找不到父订单归属: sellerId=" + sellerId + ", parentOrderSn=" + parentOrderSn);
				}
			}
		}

		Map<String, TemuParentAftersales> existMap = new HashMap<>();
		if (!sns.isEmpty()) {
			Collection<TemuAccessToken> ownershipAccounts = siteAccountMap == null ? Collections.singleton(account)
					: targetByParentOrderSn.values();
			Set<Integer> ownershipChannelIds = ownershipAccounts.stream()
				.map(TemuAccessToken::getChannelId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
			boolean includeUnmappedChannel = ownershipAccounts.stream()
				.anyMatch(target -> target.getChannelId() == null);
			existMap = temuParentAftersalesService.list(Wrappers.lambdaQuery(TemuParentAftersales.class)
				.eq(TemuParentAftersales::getSellerId, sellerId)
				.and(query -> {
					if (includeUnmappedChannel) {
						query.isNull(TemuParentAftersales::getChannelId);
						if (!ownershipChannelIds.isEmpty()) {
							query.or().in(TemuParentAftersales::getChannelId, ownershipChannelIds);
						}
					}
					else {
						query.in(TemuParentAftersales::getChannelId, ownershipChannelIds);
					}
				})
				.in(TemuParentAftersales::getParentAftersalesSn, sns))
				.stream()
				.collect(Collectors.toMap(a -> ownershipKey(a.getChannelId(), a.getSite(), a.getParentAftersalesSn()),
						a -> a, (a, b) -> a));
		}

		List<TemuParentAftersales> toSave = new ArrayList<>();
		Date now = new Date();

		for (Map<String, Object> item : pageItems) {
			TemuAccessToken targetAccount = siteAccountMap == null ? account
					: targetByParentOrderSn.get(str(item.get("parentOrderSn")));
			TemuParentAftersales parent = buildParentAftersales(item, targetAccount, now);
			if (parent.getParentAftersalesSn() == null) {
				throw new IllegalStateException("父售后单数据缺少parentAfterSalesSn: " + item.keySet());
			}

			TemuParentAftersales exist = existMap
				.get(ownershipKey(parent.getChannelId(), parent.getSite(), parent.getParentAftersalesSn()));
			if (exist != null) {
				parent.setId(exist.getId());
				parent.setCreateTime(exist.getCreateTime());
			}
			else {
				parent.setCreateTime(now);
			}
			toSave.add(parent);
		}

		if (!toSave.isEmpty()) {
			transactionTemplate.executeWithoutResult(status -> {
				temuOrderRequestTaskService.lockExecution(task);
				temuParentAftersalesService.insertOrUpdateBatch(toSave);
			});
		}

		return toSave;
	}

	@SuppressWarnings("unchecked")
	private TemuParentAftersales buildParentAftersales(Map<String, Object> map, TemuAccessToken account, Date now) {
		TemuParentAftersales p = new TemuParentAftersales();
		p.setSellerId(account.getSellerId());
		p.setChannelId(account.getChannelId());
		p.setSite(account.getSite());
		p.setParentAftersalesSn(str(map.get("parentAfterSalesSn")));
		p.setParentOrderSn(str(map.get("parentOrderSn")));
		p.setAftersalesStatusGroup(intVal(map.get("afterSalesStatusGroup")));
		p.setParentAftersalesStatus(intVal(map.get("parentAfterSalesStatus")));
		p.setAftersalesType(intVal(map.get("afterSalesType")));
		p.setReturnDeliveryType(intVal(map.get("returnDeliveryType")));

		Object createAt = map.get("createAt");
		if (createAt != null) {
			try {
				p.setCreateTimeApi(Long.parseLong(String.valueOf(createAt)));
			}
			catch (Exception ignored) {
			}
		}
		Object updateAt = map.get("updateAt");
		if (updateAt != null) {
			try {
				p.setUpdateTimeApi(Long.parseLong(String.valueOf(updateAt)));
			}
			catch (Exception ignored) {
			}
		}

		p.setUpdateTime(now);
		p.setDelFlag(0);
		return p;
	}

	// ==================== Step 2: 拉取子售后单 + 产品 ====================

	@SuppressWarnings("unchecked")
	private int pullChildAftersales(TemuAccessToken account, TemuAuthRegion authRegion,
			List<String> parentAftersalesSns, Map<String, TemuAccessToken> ownershipByParentAftersalesSn, SyncTask task) {
		String sellerId = account.getSellerId();
		int totalProcessed = 0;
		int totalFailedBatches = 0;

		// 分批调用子售后单 API
		for (int i = 0; i < parentAftersalesSns.size(); i += PARENT_SN_BATCH_SIZE) {
			int end = Math.min(i + PARENT_SN_BATCH_SIZE, parentAftersalesSns.size());
			List<String> batch = parentAftersalesSns.subList(i, end);
			int batchNo = i / PARENT_SN_BATCH_SIZE;

			int maxPage = MAX_PAGE_CAP;
			for (int page = 1; page <= maxPage; page++) {
				PageResult pr = fetchChildPage(batch, page, account, authRegion, task);

				if (pr.isFailed()) {
					totalFailedBatches++;
					log.warn("子售后单批次 {} 第 {} 页拉取失败(已重试), 跳过剩余页", batchNo, page);
					break;
				}

				if (pr.isEmpty()) {
					if (page == 1) {
						log.warn("子售后单批次 {} 返回空数据, batch大小={}", batchNo, batch.size());
					}
					break;
				}

				totalProcessed += processChildAftersalesPage(pr.getItems(), account, ownershipByParentAftersalesSn, task);

				if (pr.getTotal() > 0) {
					maxPage = Math.min(MAX_PAGE_CAP, (int) Math.ceil((double) pr.getTotal() / PAGE_SIZE));
				}
				if (pr.isLastPage()) {
					break;
				}
			}
		}

		if (totalFailedBatches > 0) {
			throw new RuntimeException(String.format("店铺 %s 子售后单有 %d 批次拉取失败, 已成功 %d 条, 需重试", sellerId,
					totalFailedBatches, totalProcessed));
		}

		log.info("店铺 {} 子售后单拉取完成: 共 {} 条", sellerId, totalProcessed);
		return totalProcessed;
	}

	/**
	 * 拉取单页子售后单数据（含失败重试一次）。
	 * @return {@link PageResult} — 成功/空/失败三态
	 * @throws TemuApiException accessToken 过期时直接向上抛
	 */
	@SuppressWarnings("unchecked")
	private PageResult fetchChildPage(List<String> batch, int page, TemuAccessToken account,
			TemuAuthRegion authRegion, SyncTask task) {
		String sellerId = account.getSellerId();
		Map<String, Object> response = RetryHelper.withRetry("店铺 " + sellerId + " 子售后单第 " + page + " 页", () -> {
			Map<String, Object> params = new HashMap<>();
			params.put("type", TemuApiType.AFTERSALES_LIST_GET.getType());
			params.put("parentAfterSalesSnList", batch);
			params.put("pageSize", PAGE_SIZE);
			params.put("pageNumber", page);
			temuOrderRequestTaskService.checkExecution(task);
			return temuApiClient.callByAuthRegion(authRegion, params, account);
		});

		if (response == null) {
			return PageResult.failed();
		}

		Map<String, Object> result = (Map<String, Object>) response.get("result");
		if (result == null) {
			log.warn("子售后单 API 返回 result 为空, batch大小={}, response keys={}", batch.size(), response.keySet());
			return PageResult.empty();
		}

		List<Map<String, Object>> items = extractDataList(result);
		if (items == null || items.isEmpty()) {
			log.warn("子售后单 API 返回 data 为空, batch大小={}, result keys={}, total={}", batch.size(), result.keySet(),
					result.get("total"));
			return PageResult.empty();
		}

		return PageResult.success(items, intVal(result.get("total")), PAGE_SIZE);
	}

	@SuppressWarnings("unchecked")
	private int processChildAftersalesPage(List<Map<String, Object>> pageItems, TemuAccessToken account,
			Map<String, TemuAccessToken> ownershipByParentAftersalesSn, SyncTask task) {
		String sellerId = account.getSellerId();
		if (pageItems == null || pageItems.isEmpty()) {
			return 0;
		}

		// 预收集子售后单号，批量查重
		List<String> sns = new ArrayList<>();
		for (Map<String, Object> item : pageItems) {
			String sn = str(item.get("afterSalesSn"));
			if (sn != null) {
				sns.add(sn);
			}
		}

		// 批量查询已存在的子售后单
		Map<String, TemuChildrenAftersales> existAftersalesMap = new HashMap<>();
		// 批量查询已存在的售后产品（按子售后单号分组）
		Map<String, List<TemuAftersalesProduct>> existProductMap = new HashMap<>();

		if (!sns.isEmpty()) {
			Set<Integer> ownershipChannelIds = ownershipByParentAftersalesSn.values()
				.stream()
				.map(TemuAccessToken::getChannelId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
			boolean includeUnmappedChannel = ownershipByParentAftersalesSn.values()
				.stream()
				.anyMatch(target -> target.getChannelId() == null);
			existAftersalesMap = temuChildrenAftersalesService.list(Wrappers.lambdaQuery(TemuChildrenAftersales.class)
				.eq(TemuChildrenAftersales::getSellerId, sellerId)
				.and(query -> {
					if (includeUnmappedChannel) {
						query.isNull(TemuChildrenAftersales::getChannelId);
						if (!ownershipChannelIds.isEmpty()) {
							query.or().in(TemuChildrenAftersales::getChannelId, ownershipChannelIds);
						}
					}
					else {
						query.in(TemuChildrenAftersales::getChannelId, ownershipChannelIds);
					}
				})
				.in(TemuChildrenAftersales::getAftersalesSn, sns))
				.stream()
				.collect(Collectors.toMap(a -> ownershipKey(a.getChannelId(), a.getSite(), a.getAftersalesSn()), a -> a,
						(a, b) -> a));

			List<TemuAftersalesProduct> existProducts = temuAftersalesProductService
				.list(Wrappers.lambdaQuery(TemuAftersalesProduct.class)
					.eq(TemuAftersalesProduct::getSellerId, sellerId)
					.and(query -> {
						if (includeUnmappedChannel) {
							query.isNull(TemuAftersalesProduct::getChannelId);
							if (!ownershipChannelIds.isEmpty()) {
								query.or().in(TemuAftersalesProduct::getChannelId, ownershipChannelIds);
							}
						}
						else {
							query.in(TemuAftersalesProduct::getChannelId, ownershipChannelIds);
						}
					})
					.in(TemuAftersalesProduct::getAftersalesSn, sns));
			existProductMap = existProducts.stream()
				.collect(Collectors.groupingBy(p -> ownershipKey(p.getChannelId(), p.getSite(), p.getAftersalesSn())));
		}

		List<TemuChildrenAftersales> aftersalesToSave = new ArrayList<>();
		List<TemuAftersalesProduct> productsToSave = new ArrayList<>();
		Date now = new Date();
		int processed = 0;

		for (Map<String, Object> item : pageItems) {
			String parentAftersalesSn = str(item.get("parentAfterSalesSn"));
			TemuAccessToken targetAccount = ownershipByParentAftersalesSn.get(parentAftersalesSn);
			if (targetAccount == null) {
				throw new IllegalStateException("子售后单找不到父售后归属: parentAftersalesSn=" + parentAftersalesSn
						+ ", aftersalesSn=" + item.get("afterSalesSn"));
			}
			TemuChildrenAftersales aftersales = buildChildAftersales(item, targetAccount, now);
			if (aftersales.getAftersalesSn() == null) {
				throw new IllegalStateException("子售后单数据缺少afterSalesSn: " + item.keySet());
			}

			// 匹配已存在记录
			String aftersalesOwnershipKey = ownershipKey(aftersales.getChannelId(), aftersales.getSite(),
					aftersales.getAftersalesSn());
			TemuChildrenAftersales exist = existAftersalesMap.get(aftersalesOwnershipKey);
			if (exist != null) {
				aftersales.setId(exist.getId());
				aftersales.setCreateTime(exist.getCreateTime());
			}
			else {
				aftersales.setCreateTime(now);
			}
			aftersalesToSave.add(aftersales);
			processed++;

			// 解析 productList → 售后产品
			Object productListObj = item.get("productList");
			Map<String, Object> goodsInfo = mapVal(item.get("afterSalesGoodsInfo"));
			if (!(productListObj instanceof List) && goodsInfo != null) {
				productListObj = goodsInfo.get("productList");
			}
			if (productListObj instanceof List) {
				List<Map<String, Object>> productList = (List<Map<String, Object>>) productListObj;

				// 构建产品，回填已存在记录的 id（saveOrUpdate 幂等）
				List<TemuAftersalesProduct> oldProducts = existProductMap.get(aftersalesOwnershipKey);
				Map<String, TemuAftersalesProduct> oldProductBySku = oldProducts == null ? Collections.emptyMap()
						: oldProducts.stream()
							.collect(Collectors.toMap(p -> String.valueOf(p.getProductSkuId()), p -> p, (a, b) -> a));
				Set<Long> currentProductSkuIds = new HashSet<>();

				for (Map<String, Object> productMap : productList) {
					TemuAftersalesProduct product = buildAftersalesProduct(productMap,
							aftersales.getParentAftersalesSn(), aftersales.getAftersalesSn(),
							aftersales.getProductSkuId(), targetAccount, now);
					if (product.getProductSkuId() == null) {
						log.warn("售后商品缺少productSkuId，跳过商品明细但保留售后单: "
								+ "sellerId={}, channelId={}, site={},afterParentAftersalesSn={}, aftersalesSn={}, fields={}",
								account.getSellerId(), account.getChannelId(), account.getSite(),
								aftersales.getParentAftersalesSn(), aftersales.getAftersalesSn(), productMap.keySet());
						continue;
					}
					if (!currentProductSkuIds.add(product.getProductSkuId())) {
						log.debug("售后商品重复，跳过: aftersalesSn={}, productSkuId={}", aftersales.getAftersalesSn(),
								product.getProductSkuId());
						continue;
					}
					TemuAftersalesProduct oldProduct = oldProductBySku.get(String.valueOf(product.getProductSkuId()));
					if (oldProduct != null) {
						product.setId(oldProduct.getId());
						product.setCreateTime(oldProduct.getCreateTime());
					}
					else {
						product.setCreateTime(now);
					}
					productsToSave.add(product);
				}
			}
		}

		// 短事务：批量 upsert（INSERT ... ON DUPLICATE KEY UPDATE）
		if (!aftersalesToSave.isEmpty()) {
			transactionTemplate.executeWithoutResult(status -> {
				temuOrderRequestTaskService.lockExecution(task);
				temuChildrenAftersalesService.insertOrUpdateBatch(aftersalesToSave);
				if (!productsToSave.isEmpty()) {
					temuAftersalesProductService.insertOrUpdateBatch(productsToSave);
				}
			});
		}

		return processed;
	}

	@SuppressWarnings("unchecked")
	private TemuChildrenAftersales buildChildAftersales(Map<String, Object> map, TemuAccessToken account, Date now) {
		TemuChildrenAftersales a = new TemuChildrenAftersales();
		a.setSellerId(account.getSellerId());
		a.setChannelId(account.getChannelId());
		a.setSite(account.getSite());

		a.setAftersalesSn(str(map.get("afterSalesSn")));
		a.setParentAftersalesSn(str(map.get("parentAfterSalesSn")));
		a.setOrderSn(str(map.get("orderSn")));

		// 状态与类型
		a.setAftersalesStatus(intVal(map.get("afterSalesStatus")));
		a.setAftersalesType(intVal(map.get("afterSalesType")));
		a.setAftersalesReasonCode(intVal(map.get("afterSalesReasonCode")));
		a.setAftersalesReasonDesc(str(map.get("afterSalesReasonDesc")));
		a.setBuyerComment(str(map.get("buyerComment")));

		// 商品信息
		a.setGoodsId(longVal(map.get("goodsId")));
		a.setSkuId(longVal(map.get("skuId")));
		Long productSkuId = longVal(map.get("productSkuId"));
		if (productSkuId == null) {
			Map<String, Object> goodsInfo = mapVal(map.get("afterSalesGoodsInfo"));
			if (goodsInfo != null) {
				productSkuId = longVal(goodsInfo.get("productSkuId"));
			}
		}
		a.setProductSkuId(productSkuId);
		a.setApplyGoodsNumber(intVal(map.get("applyAfterSalesGoodsNumber")));

		a.setUpdateTime(now);
		a.setDelFlag(0);
		return a;
	}

	@SuppressWarnings("unchecked")
	private TemuAftersalesProduct buildAftersalesProduct(Map<String, Object> map, String parentAftersalesSn,
			String aftersalesSn, Long fallbackProductSkuId, TemuAccessToken account, Date now) {
		TemuAftersalesProduct p = new TemuAftersalesProduct();
		p.setParentAftersalesSn(parentAftersalesSn);
		p.setAftersalesSn(aftersalesSn);
		p.setSellerId(account.getSellerId());
		p.setChannelId(account.getChannelId());
		p.setSite(account.getSite());
		Long productSkuId = longVal(map.get("productSkuId"));
		p.setProductSkuId(productSkuId != null ? productSkuId : fallbackProductSkuId);
		p.setExtCode(str(map.get("extCode")));
		p.setUpdateTime(now);
		p.setDelFlag(0);
		return p;
	}

	// ==================== Step 3: 拉取父售后详情 ====================

	/**
	 * 逐父售后单拉取详情，补齐售后原因、买家留言、子订单号和详情商品。 任意一条详情重试后仍失败时直接终止本任务，任务重试时依靠唯一键幂等补齐。
	 */
	@SuppressWarnings("unchecked")
	private int pullParentAftersalesDetails(TemuAccessToken account, TemuAuthRegion authRegion,
			List<TemuParentAftersales> parents, Map<String, TemuAccessToken> ownershipByParentAftersalesSn, SyncTask task) {
		int processed = 0;
		for (TemuParentAftersales parent : parents) {
			String parentSn = parent.getParentAftersalesSn();
			Map<String, Object> response = RetryHelper.withRetry("店铺 " + account.getSellerId() + " 父售后详情 " + parentSn,
					() -> {
						Map<String, Object> params = new HashMap<>();
						params.put("type", TemuApiType.AFTERSALES_PARENT_DETAIL_GET.getType());
						params.put("parentAfterSalesSn", parentSn);
						if (parent.getParentOrderSn() != null) {
							params.put("parentOrderSn", parent.getParentOrderSn());
						}
						temuOrderRequestTaskService.checkExecution(task);
						return temuApiClient.callByAuthRegion(authRegion, params, account);
					});

			if (response == null) {
				throw new IllegalStateException("父售后详情重试耗尽: " + parentSn);
			}
			Map<String, Object> result = mapVal(response.get("result"));
			if (result == null) {
				throw new IllegalStateException("父售后详情缺少result: " + parentSn);
			}
			if (!(result.get("afterSalesList") instanceof List)) {
				throw new IllegalStateException("父售后详情缺少afterSalesList: " + parentSn);
			}
			String returnedParentSn = str(result.get("parentAfterSalesSn"));
			if (returnedParentSn != null && !parentSn.equals(returnedParentSn)) {
				throw new IllegalStateException("父售后详情单号不匹配: request=" + parentSn + ", response=" + returnedParentSn);
			}

			mergeParentDetail(parent, result);
			List<Map<String, Object>> detailChildren = buildDetailChildren(result, parent);
			int childProcessed = processChildAftersalesPage(detailChildren, account, ownershipByParentAftersalesSn, task);
			if (childProcessed != detailChildren.size()) {
				throw new IllegalStateException("父售后详情子单落库不完整: parentAftersalesSn=" + parentSn + ", expected="
						+ detailChildren.size() + ", actual=" + childProcessed);
			}

			transactionTemplate.executeWithoutResult(status -> {
				temuOrderRequestTaskService.lockExecution(task);
				temuParentAftersalesService.insertOrUpdateBatch(Collections.singletonList(parent));
			});
			processed++;
		}
		log.info("店铺 {} 父售后详情拉取完成: 共 {} 条", account.getSellerId(), processed);
		return processed;
	}

	private void mergeParentDetail(TemuParentAftersales parent, Map<String, Object> result) {
		String parentOrderSn = str(result.get("parentOrderSn"));
		if (parentOrderSn != null) {
			parent.setParentOrderSn(parentOrderSn);
		}
		Integer status = intVal(result.get("parentAfterSalesStatus"));
		if (status != null) {
			parent.setParentAftersalesStatus(status);
		}
		Integer type = intVal(result.get("afterSalesType"));
		if (type != null) {
			parent.setAftersalesType(type);
		}
		Long createAtMillis = longVal(result.get("createAtMillis"));
		if (createAtMillis != null) {
			parent.setCreateTimeApi(createAtMillis / 1000L);
		}
		Long updateAtMillis = longVal(result.get("lastUpdateAtMillis"));
		if (updateAtMillis != null) {
			parent.setUpdateTimeApi(updateAtMillis / 1000L);
		}
		parent.setUpdateTime(new Date());
		parent.setDelFlag(0);
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> buildDetailChildren(Map<String, Object> result, TemuParentAftersales parent) {
		Object afterSalesListObj = result.get("afterSalesList");
		if (!(afterSalesListObj instanceof List)) {
			return Collections.emptyList();
		}
		List<Map<String, Object>> children = new ArrayList<>();
		for (Object itemObj : (List<?>) afterSalesListObj) {
			if (!(itemObj instanceof Map)) {
				continue;
			}
			Map<String, Object> item = new HashMap<>((Map<String, Object>) itemObj);
			item.put("parentAfterSalesSn", parent.getParentAftersalesSn());
			item.put("afterSalesType", parent.getAftersalesType());

			Map<String, Object> goodsInfo = mapVal(item.get("afterSalesGoodsInfo"));
			if (goodsInfo != null) {
				if (item.get("productSkuId") == null) {
					item.put("productSkuId", goodsInfo.get("productSkuId"));
				}
				if (item.get("productList") == null) {
					item.put("productList", goodsInfo.get("productList"));
				}
			}
			children.add(item);
		}
		return children;
	}

	// ==================== Step 4: 拉取退货物流 ====================

	/**
	 * 退货物流接口只在买家寄回、仓库签收或退款阶段有数据。 每次成功响应会在一个短事务内替换该父售后单的当前物流集合。
	 */
	@SuppressWarnings("unchecked")
	private int pullReturnLogistics(TemuAccessToken account, TemuAuthRegion authRegion,
			List<TemuParentAftersales> parents, Map<String, TemuAccessToken> ownershipByParentAftersalesSn, SyncTask task) {
		int processed = 0;
		for (TemuParentAftersales parent : parents) {
			if (!shouldPullReturnLogistics(parent)) {
				continue;
			}
			String parentSn = parent.getParentAftersalesSn();
			TemuAccessToken targetAccount = ownershipByParentAftersalesSn.get(parentSn);
			if (targetAccount == null) {
				throw new IllegalStateException("退货物流找不到父售后归属: parentAftersalesSn=" + parentSn);
			}
			Map<String, Object> response = RetryHelper.withRetry("店铺 " + account.getSellerId() + " 退货物流 " + parentSn,
					() -> {
						Map<String, Object> params = new HashMap<>();
						params.put("type", TemuApiType.AFTERSALES_PARENT_RETURN_ORDER_GET.getType());
						params.put("parentAfterSalesSn", parentSn);
						temuOrderRequestTaskService.checkExecution(task);
						return temuApiClient.callByAuthRegion(authRegion, params, account);
					});
			if (response == null) {
				throw new IllegalStateException("退货物流重试耗尽: " + parentSn);
			}

			Map<String, Object> result = mapVal(response.get("result"));
			List<TemuAftersalesReturnLogistics> logistics = buildReturnLogistics(result, targetAccount, parentSn);
			Date replaceTime = new Date();
			transactionTemplate.executeWithoutResult(status -> {
				temuOrderRequestTaskService.lockExecution(task);
				temuAftersalesReturnLogisticsService.softDeleteByParent(targetAccount.getChannelId(),
						targetAccount.getSite(), targetAccount.getSellerId(), parentSn, replaceTime);
				if (!logistics.isEmpty()) {
					temuAftersalesReturnLogisticsService.insertOrUpdateBatch(logistics);
				}
			});
			processed += logistics.size();
		}
		log.info("店铺 {} 退货物流拉取完成: 共 {} 条", account.getSellerId(), processed);
		return processed;
	}

	private boolean shouldPullReturnLogistics(TemuParentAftersales parent) {
		if (!Objects.equals(AFTERSALES_TYPE_RETURN_AND_REFUND, parent.getAftersalesType())) {
			return false;
		}
		Integer status = parent.getParentAftersalesStatus();
		return status != null
				&& (status == 2 || status == 3 || status == 4 || status == 5 || status == 8 || status == 9);
	}

	@SuppressWarnings("unchecked")
	private List<TemuAftersalesReturnLogistics> buildReturnLogistics(Map<String, Object> result,
			TemuAccessToken account, String parentSn) {
		if (result == null || !(result.get("logisticsInfoList") instanceof List)) {
			return Collections.emptyList();
		}
		Map<String, TemuAftersalesReturnLogistics> uniqueRows = new LinkedHashMap<>();
		Date now = new Date();
		for (Object itemObj : (List<?>) result.get("logisticsInfoList")) {
			if (!(itemObj instanceof Map)) {
				continue;
			}
			Map<String, Object> item = (Map<String, Object>) itemObj;
			String trackingNumber = str(item.get("trackingNumber"));
			if (trackingNumber == null || trackingNumber.trim().isEmpty()) {
				log.warn("父售后单 {} 退货物流缺少trackingNumber, 跳过: {}", parentSn, item.keySet());
				continue;
			}
			TemuAftersalesReturnLogistics row = new TemuAftersalesReturnLogistics();
			row.setParentAftersalesSn(parentSn);
			row.setSellerId(account.getSellerId());
			row.setChannelId(account.getChannelId());
			row.setSite(account.getSite());
			row.setCarrierName(str(item.get("carrierName")));
			row.setTrackingNumber(trackingNumber);
			row.setReturnWarehouseType(intVal(item.get("returnWarehouseType")));
			row.setReturnWarehouseRegion1Name(str(item.get("returnWarehouseRegion1Name")));
			row.setDelFlag(0);
			row.setCreateTime(now);
			row.setUpdateTime(now);
			uniqueRows.put(trackingNumber, row);
		}
		return new ArrayList<>(uniqueRows.values());
	}

	// ==================== 工具方法 ====================

	private Map<String, TemuAccessToken> buildAftersalesOwnership(List<TemuParentAftersales> parents,
			TemuAccessToken requestAccount, Map<Integer, TemuAccessToken> siteAccountMap) {
		Map<String, TemuAccessToken> result = new HashMap<>();
		for (TemuParentAftersales parent : parents) {
			TemuAccessToken target = requestAccount;
			if (siteAccountMap != null) {
				target = temuGlobalAccountResolver.resolveBySite(parent.getSite(), siteAccountMap, requestAccount,
						parent.getParentAftersalesSn());
				if (!Objects.equals(target.getChannelId(), parent.getChannelId())
						|| !Objects.equals(target.getSite(), parent.getSite())) {
					throw new IllegalStateException(
							"父售后归属没有对应Global授权: parentAftersalesSn=" + parent.getParentAftersalesSn() + ", channelId="
									+ parent.getChannelId() + ", site=" + parent.getSite());
				}
			}
			result.put(parent.getParentAftersalesSn(), target);
		}
		return result;
	}

	private boolean isGlobalRegion(TemuAuthRegion authRegion) {
		return authRegion == TemuAuthRegion.GLOBAL || authRegion == TemuAuthRegion.EU;
	}

	private static String ownershipKey(Integer channelId, String site, String businessSn) {
		return channelId + "|" + site + "|" + businessSn;
	}

	/**
	 * 从 result 中提取数据列表 — 售后 API 使用 result.data 返回列表
	 */
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> extractDataList(Map<String, Object> result) {
		Object data = result.get("data");
		if (data instanceof List) {
			return (List<Map<String, Object>>) data;
		}
		// 兼容其他可能的字段名
		Object pageItems = result.get("pageItems");
		if (pageItems instanceof List) {
			return (List<Map<String, Object>>) pageItems;
		}
		Object list = result.get("list");
		if (list instanceof List) {
			return (List<Map<String, Object>>) list;
		}
		return null;
	}

	private static String str(Object value) {
		return value == null ? null : String.valueOf(value);
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

	private void markTaskComplete(SyncTask task) {
		if (!temuOrderRequestTaskService.completeTask(task.getId(), task.getExecutionId())) {
			throw new TaskLeaseLostException("任务完成回写被拒绝: taskId=" + task.getId());
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> mapVal(Object value) {
		return value instanceof Map ? (Map<String, Object>) value : null;
	}

	private void markTaskFailed(SyncTask task, String errorMessage) {
		if (!temuOrderRequestTaskService.failTask(task.getId(), task.getExecutionId(), errorMessage)) {
			log.warn("售后任务 {} 失败状态回写被忽略，任务可能已被超时回收", task.getId());
		}
	}

}
