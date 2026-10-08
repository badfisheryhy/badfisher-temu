package com.badfisher.temu.server.integration;

import com.badfisher.temu.scheduler.xxljob.amount.TemuOrderAmountRemediationJob;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.order.model.TemuChildrenOrderAmount;
import com.badfisher.temu.business.order.model.TemuParentOrderAmount;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.business.order.service.TemuChildrenOrderAmountService;
import com.badfisher.temu.business.order.service.TemuParentOrderAmountService;
import com.badfisher.temu.business.order.service.TemuParentOrderService;
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
 * 订单金额拉取集成测试 — temuOrderAmountPullByAppSiteJob
 * <p>
 * 直接调用 {@link TemuOrderAmountRemediationJob#execute(String)}， 按 appId|US 补偿近 10
 * 天无金额记录的父订单，验证 parent/children amount 表落库。
 *
 * @author lenkor
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "TEMU_LIVE_TESTS", matches = "true")
@SpringBootTest(classes = com.badfisher.temu.server.TemuApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemuOrderAmountRemediationJobIntegrationTest {

	@Resource
	private TemuOrderAmountRemediationJob temuOrderAmountRemediationJob;

	@Resource
	private TemuAccessTokenService temuAccessTokenService;

	@Resource
	private TemuParentOrderService temuParentOrderService;

	@Resource
	private TemuParentOrderAmountService temuParentOrderAmountService;

	@Resource
	private TemuChildrenOrderAmountService temuChildrenOrderAmountService;

	@Test
	@Order(1)
	@DisplayName("步骤1 - 按 appId|US 补偿近 10 天缺失金额的订单")
	void step1_pullAmountByAppSite() {
		log.info("========== 步骤1: temuOrderAmountPullByAppSiteJob ==========");

		TemuAccessToken token = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getSite, "US")
			.eq(TemuAccessToken::getDelFlag, 0)
			.eq(TemuAccessToken::getType, 1)
			.last("LIMIT 1")
			.one();
		if (token == null) {
			throw new IllegalStateException("tb_temu_access_token 中无 US 站点记录");
		}

		String appId = token.getAppId();
		String sellerId = token.getSellerId();

		executeJob(appId + "|US", temuOrderAmountRemediationJob::execute);

		log.info("✓ 步骤1完成");
	}

	@Test
	@Order(2)
	@DisplayName("步骤2 - 验证父订单金额数据落库")
	void step2_verifyParentAmount() {
		log.info("========== 步骤2: 验证父订单金额数据 ==========");

		TemuAccessToken token = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getSite, "US")
			.eq(TemuAccessToken::getDelFlag, 0)
			.last("LIMIT 1")
			.one();
		if (token == null) {
			throw new IllegalStateException("tb_temu_access_token 中无 US 站点记录");
		}

		String sellerId = token.getSellerId();
		long count = temuParentOrderAmountService.lambdaQuery()
			.eq(TemuParentOrderAmount::getSellerId, sellerId)
			.count();
		log.info("店铺 {} 父金额记录数: {}", sellerId, count);

		List<TemuParentOrderAmount> latest = temuParentOrderAmountService.lambdaQuery()
			.eq(TemuParentOrderAmount::getSellerId, sellerId)
			.orderByDesc(TemuParentOrderAmount::getCreateTime)
			.last("LIMIT 10")
			.list();
		for (TemuParentOrderAmount p : latest) {
			log.info(
					"  id={}, parentOrderSn={}, currency={}, customerPaid={}, estimatedRevenue={}, taxTotal={}, refundsTotal={}, totalDiscount={}, shippingAmountTotal={}",
					p.getId(), p.getParentOrderSn(), p.getCurrency(), p.getCustomerPaid(), p.getEstimatedRevenue(),
					p.getTaxTotal(), p.getRefundsTotal(), p.getTotalDiscount(), p.getShippingAmountTotal());
		}
		log.info("✓ 步骤2完成");
	}

	@Test
	@Order(3)
	@DisplayName("步骤3 - 验证子订单金额数据落库")
	void step3_verifyChildrenAmount() {
		log.info("========== 步骤3: 验证子订单金额数据 ==========");

		TemuAccessToken token = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getSite, "US")
			.eq(TemuAccessToken::getDelFlag, 0)
			.last("LIMIT 1")
			.one();
		if (token == null) {
			throw new IllegalStateException("tb_temu_access_token 中无 US 站点记录");
		}

		String sellerId = token.getSellerId();
		long count = temuChildrenOrderAmountService.lambdaQuery()
			.eq(TemuChildrenOrderAmount::getSellerId, sellerId)
			.count();
		log.info("店铺 {} 子金额记录数: {}", sellerId, count);

		List<TemuChildrenOrderAmount> latest = temuChildrenOrderAmountService.lambdaQuery()
			.eq(TemuChildrenOrderAmount::getSellerId, sellerId)
			.orderByDesc(TemuChildrenOrderAmount::getCreateTime)
			.last("LIMIT 10")
			.list();
		for (TemuChildrenOrderAmount c : latest) {
			log.info(
					"  id={}, parentOrderSn={}, orderSn={}, currency={}, quantity={}, basePrice={}, unitBasePrice={}, unitRetailPrice={}, discountFromTemu={}, discountFromSeller={}, shipAmountTotal={}",
					c.getId(), c.getParentOrderSn(), c.getOrderSn(), c.getCurrency(), c.getQuantity(), c.getBasePrice(),
					c.getUnitBasePrice(), c.getUnitRetailPrice(), c.getDiscountFromTemu(), c.getDiscountFromSeller(),
					c.getShipAmountTotal());
		}
		log.info("✓ 步骤3完成");
	}

	private static void assertTrue(boolean condition, String msg) {
		if (!condition)
			throw new AssertionError(msg);
	}

}
