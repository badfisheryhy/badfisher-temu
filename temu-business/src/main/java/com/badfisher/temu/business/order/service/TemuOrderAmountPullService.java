package com.badfisher.temu.business.order.service;

import com.badfisher.temu.business.sync.service.SyncTaskService;
import com.badfisher.temu.business.sync.execution.TaskLeaseLostException;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.badfisher.temu.client.TemuApiClient;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.order.model.TemuChildrenOrderAmount;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.order.model.TemuParentOrder;
import com.badfisher.temu.business.order.model.TemuParentOrderAmount;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.badfisher.temu.client.enums.TemuApiType;
import com.badfisher.temu.client.exception.TemuApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Temu 订单金额拉取服务 — 调用 bg.order.amount.query 拉取父/子订单金额并落库(TEMU不再开放)
 * <p>
 * 响应路径: {@code data.result}
 * <ul>
 * <li>{@code parentOrderMap} → tb_temu_parent_order_amount（1 条）</li>
 * <li>{@code orderList[]} → tb_temu_children_order_amount（N 条）</li>
 * </ul>
 * 金额字段为 {@code {amount, currency}} 结构，amount 存 BIGINT，currency 存一次。
 *
 * @author lenkor
 */
@Slf4j
@Service
@Deprecated
public class TemuOrderAmountPullService {

	@Resource
	private TemuApiClient temuApiClient;

	@Resource
	private TemuParentOrderAmountService temuParentOrderAmountService;

	@Resource
	private TemuChildrenOrderAmountService temuChildrenOrderAmountService;

	@Resource
	private TemuParentOrderService temuParentOrderService;

	@Resource
	private SyncTaskService temuOrderRequestTaskService;

	@Resource
	private PlatformTransactionManager transactionManager;

	private TransactionTemplate transactionTemplate;

	@PostConstruct
	private void initTransactionTemplate() {
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/**
	 * 按授权区域返回金额查询 API type。
	 * <p>
	 * 目前已对接：
	 * <ul>
	 * <li>US — {@code bg.order.amount.query}</li>
	 * </ul>
	 * CN 站口子预留，待 type 确认后补充。
	 */
	private static String amountApiType(TemuAuthRegion authRegion) {
		switch (authRegion) {
			case US:
				return TemuApiType.ORDER_AMOUNT_QUERY.getType();
			// case CN: return ORDER_AMOUNT_API_TYPE_CN;
			default:
				throw new IllegalArgumentException("金额查询暂不支持授权区域: " + authRegion);
		}
	}

	private static final int PAGE_SIZE = 100;

	/**
	 * 任务级拉取 — 按任务时间窗口查询本地 parent_order 表，逐单调金额 API 落库。
	 * <p>
	 * 单条订单失败不中断任务；仅 token 过期等不可恢复错误标记任务失败。
	 */
	public void doPullTask(TemuAccessToken account, SyncTask task, TemuAuthRegion authRegion) {
		String sellerId = task.getAccountCode();

		try {
			temuOrderRequestTaskService.checkExecution(task);
			log.info("开始拉取订单金额: accountCode={}, beginTime={}, endTime={}", sellerId, task.getBeginTime(),
					task.getEndTime());

			int totalProcessed = 0;
			int totalFailedCount = 0;
			int page = 1;
			while (true) {
				temuOrderRequestTaskService.checkExecution(task);
				List<TemuParentOrder> pageOrders = temuParentOrderService
					.page(new Page<>(page, PAGE_SIZE),
							Wrappers.lambdaQuery(TemuParentOrder.class)
								.eq(TemuParentOrder::getSellerId, sellerId)
								.ge(TemuParentOrder::getUpdateTime, task.getBeginTime())
								.lt(TemuParentOrder::getUpdateTime, task.getEndTime())
								.select(TemuParentOrder::getParentOrderSn))
					.getRecords();
				if (pageOrders.isEmpty()) {
					break;
				}

				for (TemuParentOrder order : pageOrders) {
					boolean retried = false;
					while (true) {
						try {
							pullAmount(order.getParentOrderSn(), account, authRegion, task);
							totalProcessed++;
							break;
						}
						catch (TemuApiException e) {
							if (e.isTokenExpired()) {
								throw e;
							}
							if (!retried) {
								retried = true;
								log.warn("店铺 {} 订单 {} 金额拉取失败, 重试一次: code={}, msg={}", sellerId,
										order.getParentOrderSn(), e.getErrorCode(), e.getMessage());
								continue;
							}
							totalFailedCount++;
							log.warn("店铺 {} 订单 {} 金额拉取失败(已重试): code={}, msg={}", sellerId, order.getParentOrderSn(),
									e.getErrorCode(), e.getMessage());
							break;
						}
						catch (TaskLeaseLostException e) {
							throw e;
						}
						catch (Exception e) {
							if (!retried) {
								retried = true;
								log.warn("店铺 {} 订单 {} 金额拉取失败, 重试一次: error={}", sellerId, order.getParentOrderSn(),
										e.getMessage());
								continue;
							}
							totalFailedCount++;
							log.warn("店铺 {} 订单 {} 金额拉取失败(已重试): error={}", sellerId, order.getParentOrderSn(),
									e.getMessage());
							break;
						}
					}
				}

				if (pageOrders.size() < PAGE_SIZE) {
					break;
				}
				page++;
			}

			if (totalFailedCount > 0) {
				throw new RuntimeException(String.format("店铺 %s 有 %d 笔订单金额拉取失败, 已成功 %d 笔, 需重试", sellerId,
						totalFailedCount, totalProcessed));
			}

			log.info("店铺 {} 订单金额拉取完成: 处理 {} 条", sellerId, totalProcessed);
			markTaskComplete(task);

		}
		catch (TemuApiException e) {
			log.error("店铺 {} 订单金额拉取失败: code={}, msg={}", sellerId, e.getErrorCode(), e.getMessage());
			if (e.isTokenExpired()) {
				log.warn("店铺 {} accessToken 已过期，需提醒卖家重新授权", sellerId);
			}
			markTaskFailed(task, e.getMessage());
		}
		catch (TaskLeaseLostException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("店铺 {} 订单金额拉取失败: error={}", sellerId, e.getMessage(), e);
			markTaskFailed(task, e.getMessage());
		}
	}

	private void markTaskComplete(SyncTask task) {
		if (!temuOrderRequestTaskService.completeTask(task.getId(), task.getExecutionId())) {
			throw new TaskLeaseLostException("任务完成回写被拒绝: taskId=" + task.getId());
		}
	}

	private void markTaskFailed(SyncTask task, String errorMessage) {
		if (!temuOrderRequestTaskService.failTask(task.getId(), task.getExecutionId(), errorMessage)) {
			log.warn("金额任务 {} 失败状态回写被忽略，任务可能已被超时回收", task.getId());
		}
	}

	/**
	 * 拉取指定父订单的金额数据并落库。
	 * @return true=成功落库, false=API返回空或异常
	 */
	public boolean pullAmount(String parentOrderSn, TemuAccessToken account, TemuAuthRegion authRegion) {
		// 独立金额补救入口不使用任务表；任务链必须显式传入领取后的任务。
		return pullAmount(parentOrderSn, account, authRegion, null);
	}

	@SuppressWarnings("unchecked")
	private boolean pullAmount(String parentOrderSn, TemuAccessToken account, TemuAuthRegion authRegion,
			SyncTask task) {
		String sellerId = account.getSellerId();
		Date now = new Date();
		try {
			Map<String, Object> params = new HashMap<>();
			params.put("type", amountApiType(authRegion));
			params.put("parentOrderSn", parentOrderSn);

			if (task != null) {
				temuOrderRequestTaskService.checkExecution(task);
			}
			Map<String, Object> response = temuApiClient.callByAuthRegion(authRegion, params, account);
			Map<String, Object> result = (Map<String, Object>) response.get("result");
			if (result == null) {
				log.warn("订单 {} 金额接口返回无 result 节点", parentOrderSn);
				return false;
			}

			Map<String, Object> parentMap = (Map<String, Object>) result.get("parentOrderMap");
			if (parentMap == null || parentMap.get("parentOrderSn") == null) {
				log.warn("订单 {} 金额接口返回无 parentOrderMap", parentOrderSn);
				return false;
			}

			// ---- 父订单金额 ----
			TemuParentOrderAmount parentAmount = buildParentAmount(parentMap, sellerId, now);

			TemuParentOrderAmount existParent = temuParentOrderAmountService
				.getOne(Wrappers.lambdaQuery(TemuParentOrderAmount.class)
					.eq(TemuParentOrderAmount::getParentOrderSn, parentOrderSn)
					.eq(TemuParentOrderAmount::getSellerId, sellerId));
			if (existParent != null) {
				parentAmount.setId(existParent.getId());
				parentAmount.setCreateTime(existParent.getCreateTime());
			}
			else {
				parentAmount.setCreateTime(now);
			}

			// ---- 子订单金额 ----
			List<Map<String, Object>> orderList = (List<Map<String, Object>>) result.get("orderList");
			List<TemuChildrenOrderAmount> childrenAmounts = new ArrayList<>();
			List<Long> childIdsToRemove = new ArrayList<>();
			if (orderList != null) {
				// 按 parentOrderSn 查 DB 已有子单金额，便于对比出 API 未返回的旧记录
				Map<String, TemuChildrenOrderAmount> existChildrenMap = temuChildrenOrderAmountService
					.list(Wrappers.lambdaQuery(TemuChildrenOrderAmount.class)
						.eq(TemuChildrenOrderAmount::getSellerId, sellerId)
						.eq(TemuChildrenOrderAmount::getParentOrderSn, parentOrderSn))
					.stream()
					.collect(Collectors.toMap(TemuChildrenOrderAmount::getOrderSn, a -> a, (a, b) -> a));

				Set<String> currentOrderSns = new HashSet<>();
				for (Map<String, Object> item : orderList) {
					TemuChildrenOrderAmount child = buildChildrenAmount(item, parentOrderSn, sellerId, now);
					if (child.getOrderSn() == null)
						continue;
					TemuChildrenOrderAmount exist = existChildrenMap.get(child.getOrderSn());
					if (exist != null) {
						child.setId(exist.getId());
						child.setCreateTime(exist.getCreateTime());
					}
					else {
						child.setCreateTime(now);
					}
					childrenAmounts.add(child);
					currentOrderSns.add(child.getOrderSn());
				}

				// 计算需删除的旧记录：DB 有但本次 API 未返回的子单金额
				for (TemuChildrenOrderAmount exist : existChildrenMap.values()) {
					if (!currentOrderSns.contains(exist.getOrderSn())) {
						childIdsToRemove.add(exist.getId());
					}
				}
			}

			// ---- 落库 ----
			final TemuParentOrderAmount finalParent = parentAmount;
			final List<TemuChildrenOrderAmount> finalChildren = childrenAmounts;
			final List<Long> finalChildIdsToRemove = childIdsToRemove;
			final Date finalNow = now;
			transactionTemplate.executeWithoutResult(status -> {
				if (task != null) {
					temuOrderRequestTaskService.lockExecution(task);
				}
				temuParentOrderAmountService.saveOrUpdate(finalParent);
				// 先删除已不存在的旧子单金额
				if (!finalChildIdsToRemove.isEmpty()) {
					temuChildrenOrderAmountService.removeByIds(finalChildIdsToRemove);
				}
				if (!finalChildren.isEmpty()) {
					temuChildrenOrderAmountService.insertOrUpdateBatch(finalChildren);
				}
				// 同步刷新主订单 updateTime，便于后续补救任务按 updateTime 窗口判断是否已拉金额
				temuParentOrderService.update(Wrappers.lambdaUpdate(TemuParentOrder.class)
					.set(TemuParentOrder::getUpdateTime, finalNow)
					.eq(TemuParentOrder::getParentOrderSn, parentOrderSn)
					.eq(TemuParentOrder::getSellerId, sellerId));
			});

			log.info("订单 {} 金额拉取成功: customerPaid={}, estimatedRevenue={}, 子单数={}", parentOrderSn,
					parentAmount.getCustomerPaid(), parentAmount.getEstimatedRevenue(), childrenAmounts.size());
			return true;

		}
		catch (TaskLeaseLostException e) {
			throw e;
		}
		catch (Exception e) {
			log.warn("订单 {} 金额拉取失败: {}", parentOrderSn, e.getMessage());
			return false;
		}
	}

	// ==================== 构建方法 ====================

	@SuppressWarnings("unchecked")
	private TemuParentOrderAmount buildParentAmount(Map<String, Object> map, String sellerId, Date now) {
		TemuParentOrderAmount p = new TemuParentOrderAmount();
		p.setParentOrderSn(str(map.get("parentOrderSn")));
		p.setSellerId(sellerId);
		p.setCurrency(currency(map, "customerPaid"));
		p.setCustomerPaid(money(map, "customerPaid"));
		p.setTaxTotal(money(map, "taxTotal"));
		p.setRefundsTotal(money(map, "refundsTotal"));
		p.setEstimatedRevenue(money(map, "estimatedRevenue"));
		p.setEstimatedRevenueDeduction(money(map, "estimatedRevenueDeduction"));
		p.setDiscountFromTemu(money(map, "discountFromTEMU"));
		p.setDiscountFromSeller(money(map, "discountFromSeller"));
		p.setTotalDiscount(money(map, "totalDiscount"));
		p.setShippingAmountTotal(money(map, "shippingAmountTotal"));
		p.setBasePriceTotal(money(map, "basePriceTotal"));
		p.setRetailPriceTotal(money(map, "retailPriceTotal"));
		p.setDelFlag(0);
		p.setUpdateTime(now);
		return p;
	}

	@SuppressWarnings("unchecked")
	private TemuChildrenOrderAmount buildChildrenAmount(Map<String, Object> map, String parentOrderSn, String sellerId,
			Date now) {
		TemuChildrenOrderAmount c = new TemuChildrenOrderAmount();
		c.setParentOrderSn(parentOrderSn);
		c.setSellerId(sellerId);
		c.setOrderSn(str(map.get("orderSn")));
		c.setCurrency(currency(map, "basePrice"));
		c.setQuantity(intVal(map.get("quantity")));
		c.setDiscountFromTemu(money(map, "discountFromTEMU"));
		c.setDiscountFromSeller(money(map, "discountFromSeller"));
		c.setShipAmountTotal(money(map, "shipAmountTotal"));
		c.setUnitBasePrice(money(map, "unitBasePrice"));
		c.setUnitRetailPrice(money(map, "unitRetailPrice"));
		c.setBasePrice(money(map, "basePrice"));
		c.setDelFlag(0);
		c.setUpdateTime(now);
		return c;
	}

	// ==================== Money 工具 ====================

	@SuppressWarnings("unchecked")
	private Long money(Map<String, Object> map, String key) {
		Object obj = map.get(key);
		if (!(obj instanceof Map))
			return null;
		Object amount = ((Map<String, Object>) obj).get("amount");
		if (amount == null)
			return null;
		try {
			return Long.parseLong(String.valueOf(amount));
		}
		catch (Exception e) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private String currency(Map<String, Object> map, String key) {
		Object obj = map.get(key);
		if (!(obj instanceof Map))
			return null;
		Object currency = ((Map<String, Object>) obj).get("currency");
		return currency == null ? null : String.valueOf(currency);
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

}
