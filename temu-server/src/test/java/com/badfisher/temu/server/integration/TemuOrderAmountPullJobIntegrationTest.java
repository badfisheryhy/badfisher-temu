package com.badfisher.temu.server.integration;

import com.badfisher.temu.scheduler.xxljob.amount.TemuOrderAmountBuildJob;
import com.badfisher.temu.scheduler.xxljob.amount.TemuOrderAmountPullJob;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.order.model.TemuChildrenOrderAmount;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.order.model.TemuParentOrderAmount;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.business.order.service.TemuChildrenOrderAmountService;
import com.badfisher.temu.business.sync.service.SyncTaskService;
import com.badfisher.temu.business.order.service.TemuParentOrderAmountService;
import com.badfisher.temu.business.sync.enums.TemuPullType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;

import static com.badfisher.temu.scheduler.xxljob.XxlJobTestSupport.executeJob;

/**
 * 订单金额两阶段拉取集成测试 — TemuOrderAmountBuildJob → TemuOrderAmountPullJob → 验证落库
 * <p>
 * 验证链路： 1. TemuOrderAmountBuildJob.executeUs → 为每个 US 站点订单型店铺创建金额拉取任务(state=0) 2.
 * TemuOrderAmountPullJob.executeUs → 消费任务, 按任务窗口查本地 parent_order, 逐单调
 * bg.order.amount.query, 写入 tb_temu_parent_order_amount / tb_temu_children_order_amount
 * <p>
 * <b>数据落库, 不回滚。</b>
 * <p>
 * <b>前置条件：</b>
 * <ol>
 * <li>数据库已执行 schema.sql 建表</li>
 * <li>tb_temu_access_token 已手工插入测试店铺记录(site=US, type=1, del_flag=0,
 * app_key/app_secret/access_token/seller_id 填真实值)</li>
 * <li>tb_temu_parent_order 已有订单数据（金额拉取依赖本地父订单驱动）</li>
 * <li>数据库可达；Redis仅在分布式限流模式下需要</li>
 * </ol>
 * <p>
 * <b>注意：金额接口当前仅支持 US 站，AU/CN 的 Build/PullJob 已改为 warn 跳过，不产生任务。</b>
 *
 * @author lenkor
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "TEMU_LIVE_TESTS", matches = "true")
@SpringBootTest(classes = com.badfisher.temu.server.TemuApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemuOrderAmountPullJobIntegrationTest {

	@Resource
	private TemuOrderAmountBuildJob temuOrderAmountBuildJob;

	@Resource
	private TemuOrderAmountPullJob temuOrderAmountPullJob;

	@Resource
	private TemuAccessTokenService temuAccessTokenService;

	@Resource
	private SyncTaskService temuOrderRequestTaskService;

	@Resource
	private TemuParentOrderAmountService temuParentOrderAmountService;

	@Resource
	private TemuChildrenOrderAmountService temuChildrenOrderAmountService;

	// ==================== 1. 构建金额拉取任务 ====================

	@Test
	@Order(1)
	@DisplayName("步骤1 - TemuOrderAmountBuildJob 创建金额拉取任务")
	void step1_buildAmountTask() {
		log.info("========== 步骤1: TemuOrderAmountBuildJob.executeUs ==========");

		List<TemuAccessToken> accounts = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getSite, "US")
			.eq(TemuAccessToken::getType, TemuPullType.ORDER_AMOUNT.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.list();
		log.info("DB 中 US 站点订单型店铺数: {}", accounts.size());
		if (accounts.isEmpty()) {
			throw new IllegalStateException("tb_temu_access_token 中无 US 站点订单型店铺记录, 请先手工插入测试店铺数据");
		}
		for (TemuAccessToken acc : accounts) {
			log.info("  sellerId={}, shopName={}, appKey={}...", acc.getSellerId(), acc.getShopName(),
					acc.getAppKey() != null ? acc.getAppKey().substring(0, 8) : "(空)");
		}

		long taskBefore = temuOrderRequestTaskService.count();
		log.info("构建前 task 总数: {}", taskBefore);

		executeJob(null, temuOrderAmountBuildJob::executeUs);

		long taskAfter = temuOrderRequestTaskService.count();
		log.info("构建后 task 总数: {}", taskAfter);
		assertTrue(taskAfter > taskBefore, "BuildJob 应创建了新任务, before=" + taskBefore + ", after=" + taskAfter);

		for (TemuAccessToken acc : accounts) {
			List<SyncTask> tasks = temuOrderRequestTaskService.getUnRequestTask(acc.getChannelId(),
					acc.getSellerId(), acc.getSite(), TemuPullType.ORDER_AMOUNT.getCode());
			log.info("店铺 {} 待处理金额任务(state=0)数: {}", acc.getSellerId(), tasks.size());
			for (SyncTask t : tasks) {
				log.info("  taskId={}, beginTime={}, endTime={}, state={}", t.getId(), t.getBeginTime(), t.getEndTime(),
						t.getState());
			}
		}
		log.info("✓ 步骤1完成: 金额拉取任务已创建并落库");
	}

	// ==================== 2. 执行金额拉取 ====================

	@Test
	@Order(2)
	@DisplayName("步骤2 - TemuOrderAmountPullJob 拉取金额落库")
	void step2_pullAmount() {
		log.info("========== 步骤2: TemuOrderAmountPullJob.executeUs ==========");

		List<TemuAccessToken> accounts = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getSite, "US")
			.eq(TemuAccessToken::getType, TemuPullType.ORDER_AMOUNT.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.list();

		log.info("拉取前 DB 数据量:");
		for (TemuAccessToken acc : accounts) {
			log.info("  sellerId={}: parentAmount={}, childrenAmount={}", acc.getSellerId(),
					countParentAmount(acc.getSellerId()), countChildrenAmount(acc.getSellerId()));
		}

		executeJob(null, temuOrderAmountPullJob::executeUs);

		log.info("拉取后 DB 数据量:");
		for (TemuAccessToken acc : accounts) {
			log.info("  sellerId={}: parentAmount={}, childrenAmount={}", acc.getSellerId(),
					countParentAmount(acc.getSellerId()), countChildrenAmount(acc.getSellerId()));
		}

		for (TemuAccessToken acc : accounts) {
			SyncTask lastTask = temuOrderRequestTaskService.findLastTaskByAccountCode(acc.getChannelId(),
					acc.getSellerId(), "US", TemuPullType.ORDER_AMOUNT.getCode());
			if (lastTask != null) {
				log.info("店铺 {} 最后任务: id={}, state={}(2=完成,3=失败), error={}", acc.getSellerId(), lastTask.getId(),
						lastTask.getState(), lastTask.getErrorMessage());
			}
		}
		log.info("✓ 步骤2完成: 金额拉取执行完毕, 数据已落库");
	}

	// ==================== 3. 验证父订单金额 ====================

	@Test
	@Order(3)
	@DisplayName("步骤3 - 验证父订单金额数据落库")
	void step3_verifyParentAmount() {
		log.info("========== 步骤3: 验证父订单金额数据 ==========");

		List<TemuAccessToken> accounts = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getSite, "US")
			.eq(TemuAccessToken::getType, TemuPullType.ORDER_AMOUNT.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.list();

		for (TemuAccessToken acc : accounts) {
			String sellerId = acc.getSellerId();
			long count = countParentAmount(sellerId);
			log.info("------ 店铺 {} 父订单金额记录数: {} ------", sellerId, count);

			List<TemuParentOrderAmount> latest = temuParentOrderAmountService.lambdaQuery()
				.eq(TemuParentOrderAmount::getSellerId, sellerId)
				.orderByDesc(TemuParentOrderAmount::getCreateTime)
				.last("LIMIT 10")
				.list();
			for (TemuParentOrderAmount p : latest) {
				log.info(
						"  id={}, parentOrderSn={}, currency={}, customerPaid={}, estimatedRevenue={}, taxTotal={}, refundsTotal={}, totalDiscount={}, shippingAmountTotal={}, basePriceTotal={}, retailPriceTotal={}",
						p.getId(), p.getParentOrderSn(), p.getCurrency(), p.getCustomerPaid(), p.getEstimatedRevenue(),
						p.getTaxTotal(), p.getRefundsTotal(), p.getTotalDiscount(), p.getShippingAmountTotal(),
						p.getBasePriceTotal(), p.getRetailPriceTotal());
			}
		}
		log.info("✓ 步骤3完成: 父订单金额验证完毕");
	}

	// ==================== 4. 验证子订单金额 ====================

	@Test
	@Order(4)
	@DisplayName("步骤4 - 验证子订单金额数据落库")
	void step4_verifyChildrenAmount() {
		log.info("========== 步骤4: 验证子订单金额数据 ==========");

		List<TemuAccessToken> accounts = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getSite, "US")
			.eq(TemuAccessToken::getType, TemuPullType.ORDER_AMOUNT.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.list();

		for (TemuAccessToken acc : accounts) {
			String sellerId = acc.getSellerId();
			long count = countChildrenAmount(sellerId);
			log.info("------ 店铺 {} 子订单金额记录数: {} ------", sellerId, count);

			List<TemuChildrenOrderAmount> latest = temuChildrenOrderAmountService.lambdaQuery()
				.eq(TemuChildrenOrderAmount::getSellerId, sellerId)
				.orderByDesc(TemuChildrenOrderAmount::getCreateTime)
				.last("LIMIT 10")
				.list();
			for (TemuChildrenOrderAmount c : latest) {
				log.info(
						"  id={}, parentOrderSn={}, orderSn={}, currency={}, quantity={}, basePrice={}, unitBasePrice={}, unitRetailPrice={}, discountFromTemu={}, discountFromSeller={}, shipAmountTotal={}",
						c.getId(), c.getParentOrderSn(), c.getOrderSn(), c.getCurrency(), c.getQuantity(),
						c.getBasePrice(), c.getUnitBasePrice(), c.getUnitRetailPrice(), c.getDiscountFromTemu(),
						c.getDiscountFromSeller(), c.getShipAmountTotal());
			}
		}
		log.info("✓ 步骤4完成: 子订单金额验证完毕, 全部保留在 DB 中");
	}

	// ==================== 统计辅助 ====================

	private long countParentAmount(String sellerId) {
		return temuParentOrderAmountService.lambdaQuery().eq(TemuParentOrderAmount::getSellerId, sellerId).count();
	}

	private long countChildrenAmount(String sellerId) {
		return temuChildrenOrderAmountService.lambdaQuery().eq(TemuChildrenOrderAmount::getSellerId, sellerId).count();
	}

	private static void assertTrue(boolean condition, String msg) {
		if (!condition)
			throw new AssertionError(msg);
	}

}
