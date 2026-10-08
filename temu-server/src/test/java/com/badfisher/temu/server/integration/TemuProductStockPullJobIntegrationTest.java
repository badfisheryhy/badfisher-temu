package com.badfisher.temu.server.integration;

import com.badfisher.temu.scheduler.xxljob.product.TemuProductBuildJob;
import com.badfisher.temu.scheduler.xxljob.product.TemuProductPullJob;
import com.badfisher.temu.scheduler.xxljob.stock.TemuStockPullJob;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.product.model.TemuProduct;
import com.badfisher.temu.business.product.model.TemuProductSku;
import com.badfisher.temu.business.inventory.model.TemuProductStock;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.business.sync.service.SyncTaskService;
import com.badfisher.temu.business.product.service.TemuProductService;
import com.badfisher.temu.business.product.service.TemuProductSkuService;
import com.badfisher.temu.business.inventory.service.TemuProductStockService;
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
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

import static com.badfisher.temu.scheduler.xxljob.XxlJobTestSupport.executeJob;

/**
 * 商品与库存完整链路集成测试。
 * <p>
 * 测试按 XXL-JOB 的实际入口依次执行：创建商品任务、拉取商品、拉取库存、校验落库结果。
 * 所有接口调用和数据库写入都是真实的，测试不启用事务回滚，执行结果会保留在数据库中。
 * <p>
 * 商品接口不区分销售站点，商品授权统一按CN维护；商品、SKU和库存按appId+sellerId归档。 为避免普通单元测试误调用外部接口，仅在环境变量
 * {@code TEMU_LIVE_TESTS=true} 时启用。
 *
 * @author lenkor
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "TEMU_LIVE_TESTS", matches = "true")
@SpringBootTest(classes = com.badfisher.temu.server.TemuApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemuProductStockPullJobIntegrationTest {

	@Resource
	private TemuProductBuildJob temuProductBuildJob;

	@Resource
	private TemuProductPullJob temuProductPullJob;

	@Resource
	private TemuStockPullJob temuStockPullJob;

	@Resource
	private TemuAccessTokenService temuAccessTokenService;

	@Resource
	private SyncTaskService temuOrderRequestTaskService;

	@Resource
	private TemuProductService temuProductService;

	@Resource
	private TemuProductSkuService temuProductSkuService;

	@Resource
	private TemuProductStockService temuProductStockService;

	@Resource
	private JdbcTemplate jdbcTemplate;

	@Test
	@Order(1)
	@DisplayName("步骤1 - 模拟 temuCnProductBuildJob 创建商品任务")
	void step1_buildProductTask() {
		List<TemuAccessToken> accounts = productAccounts(TemuAuthRegion.CN);
		requireAccounts(accounts, TemuAuthRegion.CN, "请确认商品授权记录的 auth_region=CN、type=0");

		executeJob(null, temuProductBuildJob::executeCn);

		for (TemuAccessToken account : accounts) {
			List<SyncTask> tasks = pendingProductTasks(account);
			log.info("商品任务构建结果: appId={}, sellerId={}, pendingTasks={}", account.getAppId(), account.getSellerId(),
					tasks.size());
			assertTrue(!tasks.isEmpty(), "未生成待拉取商品任务 sellerId=" + account.getSellerId());
		}
	}

	@Test
	@Order(2)
	@DisplayName("步骤2 - 模拟 temuCnProductPullJob 拉取商品并落库")
	void step2_pullProducts() {
		List<TemuAccessToken> accounts = productAccounts(TemuAuthRegion.CN);
		requireAccounts(accounts, TemuAuthRegion.CN, "请确认商品授权记录的 auth_region=CN、type=0");

		executeJob(null, temuProductPullJob::executeCn);

		boolean anyProductPersisted = false;
		for (TemuAccessToken account : accounts) {
			long productCount = countProducts(account);
			long skuCount = countProductSkus(account);
			SyncTask task = temuOrderRequestTaskService.findLastTaskByAuth(account.getAppId(),
					account.getSellerId(), account.getAuthRegion(), TemuPullType.PRODUCT.getCode());
			log.info("商品拉取结果: appId={}, sellerId={}, products={}, skus={}, taskId={}, state={}, error={}",
					account.getAppId(), account.getSellerId(), productCount, skuCount,
					task == null ? null : task.getId(), task == null ? null : task.getState(),
					task == null ? null : task.getErrorMessage());
			if (productCount > 0) {
				anyProductPersisted = true;
			}
			assertTrue(task != null && Objects.equals(task.getState(), 2),
					"商品任务未成功完成 sellerId=" + account.getSellerId());
		}
		assertTrue(anyProductPersisted, "商品 Job 执行完成但没有任何商品落库");
	}

	@Test
	@Order(3)
	@DisplayName("步骤3 - 模拟 temuCnStockPullJob 拉取库存快照并落库")
	void step3_pullStockSnapshot() {
		List<TemuAccessToken> accounts = productAccounts(TemuAuthRegion.CN);
		requireAccounts(accounts, TemuAuthRegion.CN, "库存 Job 使用CN商品授权，请确认授权记录的 auth_region=CN、type=0");

		for (TemuAccessToken account : accounts) {
			long productCount = countProducts(account);
			assertTrue(productCount > 0, "库存拉取依赖商品 SKC，请先为该店铺执行商品拉取 sellerId=" + account.getSellerId());
		}

		executeJob(null, temuStockPullJob::executeCn);

		boolean anyStockPersisted = false;
		for (TemuAccessToken account : accounts) {
			List<TemuProductStock> stocks = currentStocks(account);
			log.info("库存拉取结果: appId={}, sellerId={}, currentStocks={}", account.getAppId(), account.getSellerId(),
					stocks.size());
			if (!stocks.isEmpty()) {
				anyStockPersisted = true;
				assertSingleSnapshotVersion(account, stocks);
			}
		}
		assertTrue(anyStockPersisted, "库存 Job 执行完成但没有任何当前库存快照落库");
	}

	@Test
	@Order(4)
	@DisplayName("步骤4 - 校验商品、SKU、当前库存及历史库存快照")
	void step4_verifyPersistedData() {
		List<TemuAccessToken> accounts = productAccounts(TemuAuthRegion.CN);
		requireAccounts(accounts, TemuAuthRegion.CN, "没有可校验的CN商品授权记录");

		for (TemuAccessToken account : accounts) {
			long productCount = countProducts(account);
			long skuCount = countProductSkus(account);
			List<TemuProductStock> stocks = currentStocks(account);
			Long historyCount = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM tb_temu_product_stock_snapshot_history "
							+ "WHERE app_id = ? AND seller_id = ?",
					Long.class, account.getAppId(), account.getSellerId());

			log.info("落库汇总: appId={}, sellerId={}, products={}, skus={}, currentStocks={}, historyStocks={}",
					account.getAppId(), account.getSellerId(), productCount, skuCount, stocks.size(), historyCount);
			assertTrue(productCount > 0, "商品表无数据 sellerId=" + account.getSellerId());
			assertTrue(!stocks.isEmpty(), "当前库存快照表无数据 sellerId=" + account.getSellerId());
			assertTrue(historyCount != null && historyCount > 0, "历史库存快照表无数据 sellerId=" + account.getSellerId());
			assertSingleSnapshotVersion(account, stocks);
		}
	}

	private List<TemuAccessToken> productAccounts(TemuAuthRegion authRegion) {
		return temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getAuthRegion, authRegion.getCode())
			.eq(TemuAccessToken::getType, TemuPullType.PRODUCT.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.list();
	}

	private List<SyncTask> pendingProductTasks(TemuAccessToken account) {
		return temuOrderRequestTaskService.getUnRequestTaskByAuth(account.getAppId(), account.getSellerId(),
				account.getAuthRegion(), TemuPullType.PRODUCT.getCode());
	}

	private long countProducts(TemuAccessToken account) {
		return temuProductService.lambdaQuery()
			.eq(TemuProduct::getAppId, account.getAppId())
			.eq(TemuProduct::getSellerId, account.getSellerId())
			.count();
	}

	private long countProductSkus(TemuAccessToken account) {
		return temuProductSkuService.lambdaQuery()
			.eq(TemuProductSku::getAppId, account.getAppId())
			.eq(TemuProductSku::getSellerId, account.getSellerId())
			.count();
	}

	private List<TemuProductStock> currentStocks(TemuAccessToken account) {
		return temuProductStockService.lambdaQuery()
			.eq(TemuProductStock::getAppId, account.getAppId())
			.eq(TemuProductStock::getSellerId, account.getSellerId())
			.list();
	}

	private void assertSingleSnapshotVersion(TemuAccessToken account, List<TemuProductStock> stocks) {
		long versionCount = stocks.stream()
			.map(TemuProductStock::getSnapshotVersion)
			.filter(Objects::nonNull)
			.distinct()
			.count();
		assertTrue(versionCount == 1,
				"当前库存存在多个快照版本 sellerId=" + account.getSellerId() + ", versionCount=" + versionCount);
	}

	private static void requireAccounts(List<TemuAccessToken> accounts, TemuAuthRegion authRegion, String message) {
		if (accounts.isEmpty()) {
			throw new IllegalStateException(message + " (authRegion=" + authRegion.getCode() + ")");
		}
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

}
