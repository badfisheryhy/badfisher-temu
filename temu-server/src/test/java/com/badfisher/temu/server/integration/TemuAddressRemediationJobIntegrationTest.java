package com.badfisher.temu.server.integration;

import com.badfisher.temu.scheduler.xxljob.order.TemuAddressRemediationJob;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.order.model.TemuOrderAddress;
import com.badfisher.temu.business.order.model.TemuParentOrder;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.business.order.service.TemuOrderAddressService;
import com.badfisher.temu.business.order.service.TemuParentOrderService;
import com.badfisher.temu.client.enums.TemuAuthRegion;
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
 * 订单地址补救集成测试 — TemuAddressRemediationJob.execute(null) 扫描全部启用店铺, 为缺失地址或 mail
 * 为空的父订单补充收货地址, 验证 tb_temu_order_address 落库。
 * <p>
 * 验证链路： TemuAddressRemediationJob.execute(null) → 遍历启用订单型店铺 → 查 parent_order 与
 * order_address 差集（无地址 / mail 为空） → 调 bg.order.shippinginfo.v2.get 补地址 → upsert
 * tb_temu_order_address + 回填 parent_order.buyer_name
 * <p>
 * <b>数据落库, 不回滚。</b>
 * <p>
 * <b>前置条件：</b>
 * <ol>
 * <li>数据库已执行 schema.sql 建表</li>
 * <li>tb_temu_access_token 已手工插入测试店铺记录(type=1, del_flag=0,
 * app_key/app_secret/access_token/seller_id 填真实值)</li>
 * <li>tb_temu_parent_order 已有订单数据（补救目标来源于父订单表）</li>
 * <li>数据库可达；Redis仅在分布式限流模式下需要</li>
 * </ol>
 *
 * @author lenkor
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "TEMU_LIVE_TESTS", matches = "true")
@SpringBootTest(classes = com.badfisher.temu.server.TemuApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemuAddressRemediationJobIntegrationTest {

	@Resource
	private TemuAddressRemediationJob temuAddressRemediationJob;

	@Resource
	private TemuAccessTokenService temuAccessTokenService;

	@Resource
	private TemuParentOrderService temuParentOrderService;

	@Resource
	private TemuOrderAddressService temuOrderAddressService;

	// ==================== 1. 统计补救前缺失情况 ====================

	@Test
	@Order(1)
	@DisplayName("步骤1 - 统计补救前缺失地址的父订单")
	void step1_statBeforeRemediation() {
		log.info("========== 步骤1: 统计补救前缺失地址的父订单 ==========");

		List<TemuAccessToken> accounts = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getType, TemuPullType.ORDER.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.list();
		if (accounts.isEmpty()) {
			throw new IllegalStateException("tb_temu_access_token 中无启用订单型店铺记录, 请先手工插入测试店铺数据");
		}
		long globalMappingCount = accounts.stream()
			.filter(account -> TemuAuthRegion.GLOBAL.getCode().equalsIgnoreCase(account.getAuthRegion()))
			.count();
		assertTrue(globalMappingCount >= 2, "GLOBAL地址补救测试至少需要两条有效业务站点映射");
		for (TemuAccessToken acc : accounts) {
			log.info("  sellerId={}, authRegion={}, site={}, channelId={}, shopName={}, appKey={}...",
					acc.getSellerId(), acc.getAuthRegion(), acc.getSite(), acc.getChannelId(), acc.getShopName(),
					acc.getAppKey() != null ? acc.getAppKey().substring(0, 8) : "(空)");
		}

		for (TemuAccessToken acc : accounts) {
			long parentCount = countParent(acc);
			long addrCount = countAddress(acc);
			long mailNullCount = temuOrderAddressService.lambdaQuery()
				.eq(TemuOrderAddress::getSellerId, acc.getSellerId())
				.eq(TemuOrderAddress::getChannelId, acc.getChannelId())
				.eq(TemuOrderAddress::getSite, acc.getSite())
				.isNull(TemuOrderAddress::getMail)
				.count();
			log.info("店铺 sellerId={}, site={}, channelId={}: parent={}, address={}, address中mail为空={}",
					acc.getSellerId(), acc.getSite(), acc.getChannelId(), parentCount, addrCount, mailNullCount);
		}
		log.info("✓ 步骤1完成: 缺失情况统计完毕");
	}

	// ==================== 2. 执行地址补救 ====================

	@Test
	@Order(2)
	@DisplayName("步骤2 - TemuAddressRemediationJob 扫描全部店铺补救地址")
	void step2_remediateAddress() {
		log.info("========== 步骤2: TemuAddressRemediationJob.execute(null) ==========");

		// param 为空 → 扫描所有启用订单型店铺
		executeJob(null, temuAddressRemediationJob::execute);

		log.info("✓ 步骤2完成: 地址补救执行完毕");
	}

	// ==================== 3. 验证地址落库 ====================

	@Test
	@Order(3)
	@DisplayName("步骤3 - 验证地址数据落库")
	void step3_verifyAddress() {
		log.info("========== 步骤3: 验证地址数据 ==========");

		List<TemuAccessToken> accounts = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getType, TemuPullType.ORDER.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.list();

		long globalAddressCount = 0;
		for (TemuAccessToken acc : accounts) {
			String sellerId = acc.getSellerId();
			long parentCount = countParent(acc);
			long addrCount = countAddress(acc);
			long mailNullCount = temuOrderAddressService.lambdaQuery()
				.eq(TemuOrderAddress::getSellerId, sellerId)
				.eq(TemuOrderAddress::getChannelId, acc.getChannelId())
				.eq(TemuOrderAddress::getSite, acc.getSite())
				.isNull(TemuOrderAddress::getMail)
				.count();
			log.info("------ 店铺 sellerId={}, site={}, channelId={}: parent={}, address={}, mail为空={} ------", sellerId,
					acc.getSite(), acc.getChannelId(), parentCount, addrCount, mailNullCount);

			List<TemuOrderAddress> samples = temuOrderAddressService.lambdaQuery()
				.eq(TemuOrderAddress::getSellerId, sellerId)
				.eq(TemuOrderAddress::getChannelId, acc.getChannelId())
				.eq(TemuOrderAddress::getSite, acc.getSite())
				.orderByDesc(TemuOrderAddress::getUpdateTime)
				.last("LIMIT 10")
				.list();
			for (TemuOrderAddress a : samples) {
				log.info(
						"  id={}, parentOrderSn={}, receiptName={}, mobile={}, mail={}, region1={}, region2={}, region3={}, postCode={}",
						a.getId(), a.getParentOrderSn(), a.getReceiptName(), a.getMobile(), a.getMail(),
						a.getRegionname1(), a.getRegionname2(), a.getRegionname3(), a.getPostCode());
			}

			// 唯一索引自检：同一 channelId/site 下 parentOrderSn 不应重复
			long distinct = temuOrderAddressService.lambdaQuery()
				.eq(TemuOrderAddress::getSellerId, sellerId)
				.eq(TemuOrderAddress::getChannelId, acc.getChannelId())
				.eq(TemuOrderAddress::getSite, acc.getSite())
				.select(TemuOrderAddress::getParentOrderSn)
				.list()
				.stream()
				.map(TemuOrderAddress::getParentOrderSn)
				.distinct()
				.count();
			assertTrue(distinct == addrCount, "唯一索引校验失败 sellerId=" + sellerId + ", site=" + acc.getSite()
					+ ", channelId=" + acc.getChannelId() + ": total=" + addrCount + ", distinct=" + distinct);
			if (TemuAuthRegion.GLOBAL.getCode().equalsIgnoreCase(acc.getAuthRegion())) {
				globalAddressCount += addrCount;
			}
		}
		assertTrue(globalAddressCount > 0, "GLOBAL业务站点没有订单地址落库，请先完成GLOBAL订单拉取并检查地址接口");
		log.info("✓ 步骤3完成: 地址数据验证完毕, (channel_id, site, parent_order_sn) 唯一性校验通过");
	}

	// ==================== 4. 单笔补救参数校验（异常路径） ====================

	@Test
	@Order(4)
	@DisplayName("步骤4 - 单笔补救异常参数格式校验（不抛异常, 仅 warn 日志）")
	void step4_singleRemediationBadParam() {
		log.info("========== 步骤4: TemuAddressRemediationJob.execute(非法参数) ==========");

		// 非法参数格式（无冒号）应仅打 warn 日志, 不抛异常
		executeJob("badparam-no-colon", temuAddressRemediationJob::execute);
		// 空参数走全量扫描路径, 不应抛异常
		executeJob("", temuAddressRemediationJob::execute);
		// 仅冒号无单号
		executeJob("sellerOnly:", temuAddressRemediationJob::execute);

		log.info("✓ 步骤4完成: 非法参数均安全跳过, 未抛异常");
	}

	// ==================== 统计辅助 ====================

	private long countParent(TemuAccessToken account) {
		return temuParentOrderService.lambdaQuery()
			.eq(TemuParentOrder::getSellerId, account.getSellerId())
			.eq(TemuParentOrder::getChannelId, account.getChannelId())
			.eq(TemuParentOrder::getSite, account.getSite())
			.count();
	}

	private long countAddress(TemuAccessToken account) {
		return temuOrderAddressService.lambdaQuery()
			.eq(TemuOrderAddress::getSellerId, account.getSellerId())
			.eq(TemuOrderAddress::getChannelId, account.getChannelId())
			.eq(TemuOrderAddress::getSite, account.getSite())
			.count();
	}

	private static void assertTrue(boolean condition, String msg) {
		if (!condition)
			throw new AssertionError(msg);
	}

}
