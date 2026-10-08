package com.badfisher.temu.server.integration;

import com.badfisher.temu.business.warehouse.service.TemuWarehousePullService;

import com.badfisher.temu.scheduler.xxljob.logistics.TemuWarehousePullJob;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.warehouse.model.TemuWarehouse;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.business.warehouse.service.TemuWarehouseService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.badfisher.temu.scheduler.xxljob.XxlJobTestSupport.executeJob;

/**
 * TemuWarehousePullJob 集成测试 — 调用US/GLOBAL仓库Job落库并校验。
 * <p>
 * 验证链路：TemuWarehousePullJob.executeUs/executeGlobal → TemuWarehousePullService.doPullTask
 * → bg.logistics.warehouse.list.get → tb_temu_warehouse upsert。
 * <p>
 * <b>数据落库, 不回滚。</b>
 * <p>
 * <b>前置条件：</b>
 * <ol>
 * <li>已执行 schema.sql（含 tb_temu_warehouse）</li>
 * <li>tb_temu_access_token 已插入US和GLOBAL订单授权</li>
 * <li>数据库可达；Redis仅在分布式限流模式下需要</li>
 * </ol>
 *
 * @author lenkor
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "TEMU_LIVE_TESTS", matches = "true")
@SpringBootTest(classes = com.badfisher.temu.server.TemuApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemuWarehousePullJobIntegrationTest {

	@Resource
	private TemuWarehousePullJob temuWarehousePullJob;

	@Resource
	private TemuAccessTokenService temuAccessTokenService;

	@Resource
	private TemuWarehouseService temuWarehouseService;

	@Test
	@Order(1)
	@DisplayName("temuUsWarehousePullJob - 拉取仓库列表并校验落库")
	void step1_pullAndVerifyUsWarehouse() {
		log.info("========== temuUsWarehousePullJob 落库测试 开始 ==========");

		List<TemuAccessToken> accounts = orderAccounts(TemuAuthRegion.US);
		if (accounts.isEmpty()) {
			throw new IllegalStateException("tb_temu_access_token 中无US订单授权记录");
		}
		for (TemuAccessToken acc : accounts) {
			log.info("  sellerId={}, shopName={}, appKey={}...", acc.getSellerId(), acc.getShopName(),
					acc.getAppKey() != null ? acc.getAppKey().substring(0, 8) : "(空)");
		}

		log.info("拉取前各店铺仓库数:");
		for (TemuAccessToken acc : accounts) {
			log.info("  sellerId={}: {}", acc.getSellerId(), countWarehouse(acc.getSellerId()));
		}

		executeJob(null, temuWarehousePullJob::executeUs);

		verifyMappingsPersisted(accounts);
		log.info("✓ US测试通过: 仓库已落库, (seller_id, warehouse_id) 唯一性校验通过");
	}

	@Test
	@Order(2)
	@DisplayName("temuGlobalWarehousePullJob - GLOBAL按授权主体只请求并落库一次")
	void step2_pullAndVerifyGlobalWarehouse() {
		log.info("========== temuGlobalWarehousePullJob 落库测试 开始 ==========");
		List<TemuAccessToken> mappings = orderAccounts(TemuAuthRegion.GLOBAL);
		Map<String, List<TemuAccessToken>> groups = groupByRequestIdentity(mappings);
		boolean hasMultiSiteGroup = groups.values().stream().anyMatch(group -> group.size() > 1);
		assertTrue(hasMultiSiteGroup, "GLOBAL仓库测试至少需要一个授权请求身份对应多个业务channel");

		executeJob(null, temuWarehousePullJob::executeGlobal);

		verifyMappingsPersisted(mappings);
		for (Map.Entry<String, List<TemuAccessToken>> entry : groups.entrySet()) {
			log.info("GLOBAL仓库请求身份={}，已同步业务channel={}", entry.getKey(),
					entry.getValue().stream().map(TemuAccessToken::getChannelId).collect(Collectors.toList()));
		}
		log.info("✓ GLOBAL测试通过: 每个授权主体只拉一次，仓库数据不按channel复制");
	}

	private List<TemuAccessToken> orderAccounts(TemuAuthRegion authRegion) {
		return temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getAuthRegion, authRegion.getCode())
			.eq(TemuAccessToken::getType, TemuPullType.ORDER.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.isNotNull(TemuAccessToken::getChannelId)
			.orderByAsc(TemuAccessToken::getId)
			.list();
	}

	private Map<String, List<TemuAccessToken>> groupByRequestIdentity(List<TemuAccessToken> accounts) {
		Map<String, List<TemuAccessToken>> groups = new LinkedHashMap<>();
		for (TemuAccessToken account : accounts) {
			String key = account.getAppId() + "|" + account.getSellerId() + "|" + account.getAuthRegion() + "|"
					+ account.getType();
			groups.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(account);
		}
		return groups;
	}

	private void verifyMappingsPersisted(List<TemuAccessToken> accounts) {
		for (TemuAccessToken acc : accounts) {
			long count = countWarehouse(acc.getSellerId());
			log.info("------ sellerId={}, site={}, 仓库数={} ------", acc.getSellerId(), acc.getSite(), count);
			assertTrue(count > 0, "sellerId=" + acc.getSellerId() + " 没有仓库落库，请检查授权和API返回");
			List<TemuWarehouse> rows = temuWarehouseService.lambdaQuery()
				.eq(TemuWarehouse::getSellerId, acc.getSellerId())
				.orderByDesc(TemuWarehouse::getUpdateTime)
				.list();
			long distinct = rows.stream().map(TemuWarehouse::getWarehouseId).distinct().count();
			assertTrue(distinct == count,
					"唯一索引校验失败 sellerId=" + acc.getSellerId() + ": total=" + count + ", distinct=" + distinct);
			for (TemuWarehouse warehouse : rows.stream().limit(10).collect(Collectors.toList())) {
				log.info("  warehouseId={}, name={}, regionId1={}, mgmtType={}", warehouse.getWarehouseId(),
						warehouse.getWarehouseName(), warehouse.getRegionId1(), warehouse.getWarehouseManagementType());
			}
		}
	}

	private long countWarehouse(String sellerId) {
		return temuWarehouseService.lambdaQuery().eq(TemuWarehouse::getSellerId, sellerId).count();
	}

	private static void assertTrue(boolean condition, String msg) {
		if (!condition)
			throw new AssertionError(msg);
	}

}
