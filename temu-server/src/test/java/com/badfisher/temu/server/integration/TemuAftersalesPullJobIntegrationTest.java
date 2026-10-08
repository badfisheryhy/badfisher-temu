package com.badfisher.temu.server.integration;

import com.badfisher.temu.scheduler.xxljob.aftersales.TemuAftersalesBuildJob;
import com.badfisher.temu.scheduler.xxljob.aftersales.TemuAftersalesPullJob;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.aftersales.model.TemuAftersalesProduct;
import com.badfisher.temu.business.aftersales.model.TemuAftersalesReturnLogistics;
import com.badfisher.temu.business.aftersales.model.TemuChildrenAftersales;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.aftersales.model.TemuParentAftersales;
import com.badfisher.temu.business.order.model.TemuParentOrder;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.business.aftersales.service.TemuAftersalesProductService;
import com.badfisher.temu.business.aftersales.service.TemuAftersalesReturnLogisticsService;
import com.badfisher.temu.business.aftersales.service.TemuChildrenAftersalesService;
import com.badfisher.temu.business.sync.service.SyncTaskService;
import com.badfisher.temu.business.aftersales.service.TemuParentAftersalesService;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.badfisher.temu.scheduler.xxljob.XxlJobTestSupport.executeJob;

/**
 * 售后单拉取集成测试 — BuildJob → PullJob → 验证落库
 * <p>
 * 验证完整拉取：父售后列表 → 子售后及商品 → 父售后详情 → 退货物流。 测试产生的任务及售后数据会真实写入数据库，不回滚。
 *
 * @author lenkor
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "TEMU_LIVE_TESTS", matches = "true")
@SpringBootTest(classes = com.badfisher.temu.server.TemuApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemuAftersalesPullJobIntegrationTest {

	@Resource
	private TemuAftersalesBuildJob temuAftersalesBuildJob;

	@Resource
	private TemuAftersalesPullJob temuAftersalesPullJob;

	@Resource
	private TemuAccessTokenService temuAccessTokenService;

	@Resource
	private SyncTaskService temuOrderRequestTaskService;

	@Resource
	private TemuParentAftersalesService temuParentAftersalesService;

	@Resource
	private TemuParentOrderService temuParentOrderService;

	@Resource
	private TemuChildrenAftersalesService temuChildrenAftersalesService;

	@Resource
	private TemuAftersalesProductService temuAftersalesProductService;

	@Resource
	private TemuAftersalesReturnLogisticsService temuAftersalesReturnLogisticsService;

	@Test
	@Order(1)
	@DisplayName("步骤1 - 售后BuildJob 创建拉取任务")
	void step1_buildTask() {
		log.info("========== 步骤1: TemuAftersalesBuildJob.executeUs ==========");

		List<TemuAccessToken> accounts = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getAuthRegion, TemuAuthRegion.US.getCode())
			.eq(TemuAccessToken::getType, TemuPullType.AFTERSALES.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.list();
		if (accounts.isEmpty()) {
			throw new IllegalStateException("tb_temu_access_token 中无 US 站点记录");
		}
		log.info("DB 中 US 售后授权店铺数: {}", accounts.size());
		for (TemuAccessToken account : accounts) {
			log.info("  appId={}, sellerId={}, channelId={}, shopName={}", account.getAppId(), account.getSellerId(),
					account.getChannelId(), account.getShopName());
		}

		long taskBefore = temuOrderRequestTaskService.count();
		executeJob(null, temuAftersalesBuildJob::executeUs);
		long taskAfter = temuOrderRequestTaskService.count();
		log.info("task 总数: before={}, after={}", taskBefore, taskAfter);
		assertTrue(taskAfter > taskBefore, "BuildJob 应创建了新任务");

		for (TemuAccessToken acc : accounts) {
			List<SyncTask> tasks = temuOrderRequestTaskService.getUnRequestTaskByAuth(acc.getAppId(),
					acc.getSellerId(), acc.getAuthRegion(), TemuPullType.AFTERSALES.getCode());
			log.info("店铺 {} 待处理售后任务(state=0)数: {}", acc.getSellerId(), tasks.size());
			for (SyncTask task : tasks) {
				log.info("  taskId={}, window=[{} - {}], state={}", task.getId(), task.getBeginTime(),
						task.getEndTime(), task.getState());
			}
		}
		log.info("✓ 步骤1完成: 售后拉取任务已落库");
	}

	@Test
	@Order(2)
	@DisplayName("步骤2 - 售后PullJob 拉取落库")
	void step2_pullAftersales() {
		log.info("========== 步骤2: TemuAftersalesPullJob.executeUs ==========");

		long parentBefore = temuParentAftersalesService.count();
		long childBefore = temuChildrenAftersalesService.count();
		long productBefore = temuAftersalesProductService.count();
		long logisticsBefore = temuAftersalesReturnLogisticsService.count();

		List<Long> pendingTaskIds = findPendingTaskIds(TemuAuthRegion.US);
		assertTrue(!pendingTaskIds.isEmpty(), "没有待拉取的售后任务，请先执行步骤1");

		executeJob(null, temuAftersalesPullJob::executeUs);

		long parentAfter = temuParentAftersalesService.count();
		long childAfter = temuChildrenAftersalesService.count();
		long productAfter = temuAftersalesProductService.count();
		long logisticsAfter = temuAftersalesReturnLogisticsService.count();
		log.info("父售后单总数: before={}, after={}", parentBefore, parentAfter);
		log.info("子售后单总数: before={}, after={}", childBefore, childAfter);
		log.info("售后商品总数: before={}, after={}", productBefore, productAfter);
		log.info("退货物流总数: before={}, after={}", logisticsBefore, logisticsAfter);

		for (Long taskId : pendingTaskIds) {
			SyncTask task = temuOrderRequestTaskService.getById(taskId);
			assertTrue(task != null, "售后任务不存在: taskId=" + taskId);
			assertTrue(Integer.valueOf(2).equals(task.getState()),
					"售后任务执行失败: taskId=" + taskId + ", state=" + task.getState() + ", error=" + task.getErrorMessage());
		}

		List<TemuAccessToken> accounts = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getAuthRegion, TemuAuthRegion.US.getCode())
			.eq(TemuAccessToken::getType, TemuPullType.AFTERSALES.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.list();
		for (TemuAccessToken acc : accounts) {
			SyncTask lastTask = temuOrderRequestTaskService.findLastTaskByAuth(acc.getAppId(),
					acc.getSellerId(), acc.getAuthRegion(), TemuPullType.AFTERSALES.getCode());
			if (lastTask != null) {
				log.info("店铺 {} 最后任务: id={}, state={}(2=完成,3=失败), error={}", acc.getSellerId(), lastTask.getId(),
						lastTask.getState(), lastTask.getErrorMessage());
			}
		}
		log.info("✓ 步骤2完成: {} 个售后任务执行成功，数据已落库", pendingTaskIds.size());
	}

	@Test
	@Order(3)
	@DisplayName("步骤3 - 验证父售后单数据落库")
	void step3_verifyParentData() {
		log.info("========== 步骤3: 验证父售后单数据 ==========");

		List<TemuAccessToken> accounts = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getAuthRegion, TemuAuthRegion.US.getCode())
			.eq(TemuAccessToken::getType, TemuPullType.AFTERSALES.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.list();

		for (TemuAccessToken acc : accounts) {
			String sellerId = acc.getSellerId();
			long count = temuParentAftersalesService.lambdaQuery()
				.eq(TemuParentAftersales::getSellerId, sellerId)
				.eq(TemuParentAftersales::getChannelId, acc.getChannelId())
				.eq(TemuParentAftersales::getSite, acc.getSite())
				.count();
			log.info("店铺 {} 父售后单数: {}", sellerId, count);

			List<TemuParentAftersales> latest = temuParentAftersalesService.lambdaQuery()
				.eq(TemuParentAftersales::getSellerId, sellerId)
				.eq(TemuParentAftersales::getChannelId, acc.getChannelId())
				.eq(TemuParentAftersales::getSite, acc.getSite())
				.orderByDesc(TemuParentAftersales::getCreateTime)
				.last("LIMIT 10")
				.list();
			for (TemuParentAftersales p : latest) {
				log.info("  id={}, parentAftersalesSn={}, parentOrderSn={}, status={}, statusGroup={}, type={}",
						p.getId(), p.getParentAftersalesSn(), p.getParentOrderSn(), p.getParentAftersalesStatus(),
						p.getAftersalesStatusGroup(), p.getAftersalesType());
			}
		}
		assertTrue(temuParentAftersalesService.count() > 0, "父售后表没有数据，请确认测试时间窗口内存在售后单");
		log.info("✓ 步骤3完成");
	}

	@Test
	@Order(4)
	@DisplayName("步骤4 - 验证子售后单数据落库")
	void step4_verifyChildrenData() {
		log.info("========== 步骤4: 验证子售后单数据 ==========");

		List<TemuAccessToken> accounts = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getAuthRegion, TemuAuthRegion.US.getCode())
			.eq(TemuAccessToken::getType, TemuPullType.AFTERSALES.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.list();

		for (TemuAccessToken acc : accounts) {
			String sellerId = acc.getSellerId();
			long count = temuChildrenAftersalesService.lambdaQuery()
				.eq(TemuChildrenAftersales::getSellerId, sellerId)
				.eq(TemuChildrenAftersales::getChannelId, acc.getChannelId())
				.eq(TemuChildrenAftersales::getSite, acc.getSite())
				.count();
			log.info("店铺 {} 子售后单数: {}", sellerId, count);

			List<TemuChildrenAftersales> latest = temuChildrenAftersalesService.lambdaQuery()
				.eq(TemuChildrenAftersales::getSellerId, sellerId)
				.eq(TemuChildrenAftersales::getChannelId, acc.getChannelId())
				.eq(TemuChildrenAftersales::getSite, acc.getSite())
				.orderByDesc(TemuChildrenAftersales::getCreateTime)
				.last("LIMIT 10")
				.list();
			for (TemuChildrenAftersales a : latest) {
				log.info(
						"  id={}, aftersalesSn={}, parentAftersalesSn={}, orderSn={}, status={}, type={}, reasonCode={}, reasonDesc={}, productSkuId={}, applyNum={}",
						a.getId(), a.getAftersalesSn(), a.getParentAftersalesSn(), a.getOrderSn(),
						a.getAftersalesStatus(), a.getAftersalesType(), a.getAftersalesReasonCode(),
						a.getAftersalesReasonDesc(), a.getProductSkuId(), a.getApplyGoodsNumber());
			}
		}
		assertTrue(temuChildrenAftersalesService.count() > 0, "子售后表没有数据，请检查子售后接口返回及落库逻辑");
		log.info("✓ 步骤4完成");
	}

	@Test
	@Order(5)
	@DisplayName("步骤5 - 验证售后商品和退货物流落库")
	void step5_verifyProductAndReturnLogistics() {
		log.info("========== 步骤5: 验证售后商品和退货物流数据 ==========");

		List<TemuAftersalesProduct> products = temuAftersalesProductService.lambdaQuery()
			.orderByDesc(TemuAftersalesProduct::getCreateTime)
			.last("LIMIT 20")
			.list();
		for (TemuAftersalesProduct product : products) {
			log.info("  售后商品: parentAftersalesSn={}, aftersalesSn={}, sellerId={}, productSkuId={}, extCode={}",
					product.getParentAftersalesSn(), product.getAftersalesSn(), product.getSellerId(),
					product.getProductSkuId(), product.getExtCode());
		}

		List<TemuAftersalesReturnLogistics> latest = temuAftersalesReturnLogisticsService.lambdaQuery()
			.orderByDesc(TemuAftersalesReturnLogistics::getCreateTime)
			.last("LIMIT 20")
			.list();
		for (TemuAftersalesReturnLogistics row : latest) {
			log.info("  parentAftersalesSn={}, sellerId={}, carrier={}, trackingNumber={}, warehouseType={}, region={}",
					row.getParentAftersalesSn(), row.getSellerId(), row.getCarrierName(), row.getTrackingNumber(),
					row.getReturnWarehouseType(), row.getReturnWarehouseRegion1Name());
		}
		log.info("售后商品总数={}", temuAftersalesProductService.count());
		log.info("退货物流总数={}", temuAftersalesReturnLogisticsService.count());
		log.info("✓ 步骤5完成");
	}

	@Test
	@Order(6)
	@DisplayName("步骤6 - GLOBAL售后BuildJob按授权主体创建唯一任务")
	void step6_buildGlobalTask() {
		log.info("========== 步骤6: TemuAftersalesBuildJob.executeGlobal ==========");
		List<TemuAccessToken> mappings = globalAftersalesAccounts();
		if (mappings.isEmpty()) {
			throw new IllegalStateException("无 auth_region=GLOBAL 的售后授权记录");
		}
		Map<String, TemuAccessToken> requestAccounts = uniqueRequestAccounts(mappings);
		assertTrue(mappings.size() > requestAccounts.size(),
				"GLOBAL测试数据至少需要一个请求身份对应多条业务站点映射，当前映射数=" + mappings.size() + ", 请求主体数=" + requestAccounts.size());

		long taskBefore = temuOrderRequestTaskService.count();
		executeJob(null, temuAftersalesBuildJob::executeGlobal);
		long taskAfter = temuOrderRequestTaskService.count();
		assertTrue(taskAfter > taskBefore, "GLOBAL售后BuildJob应创建新任务");

		for (TemuAccessToken account : requestAccounts.values()) {
			SyncTask task = temuOrderRequestTaskService.findLastTaskByAuth(account.getAppId(),
					account.getSellerId(), TemuAuthRegion.GLOBAL.getCode(), TemuPullType.AFTERSALES.getCode());
			assertTrue(task != null,
					"GLOBAL售后任务未创建: appId=" + account.getAppId() + ", sellerId=" + account.getSellerId());
			assertTrue(Integer.valueOf(0).equals(task.getState()),
					"GLOBAL售后任务构建后应为state=0: taskId=" + task.getId() + ", state=" + task.getState());
			log.info("GLOBAL售后任务: taskId={}, appId={}, sellerId={}, window=[{} - {}]", task.getId(), task.getAppId(),
					task.getAccountCode(), task.getBeginTime(), task.getEndTime());
		}
		log.info("✓ 步骤6完成: GLOBAL售后任务已按请求身份去重落库");
	}

	@Test
	@Order(7)
	@DisplayName("步骤7 - GLOBAL售后PullJob执行并落库")
	void step7_pullGlobalAftersales() {
		log.info("========== 步骤7: TemuAftersalesPullJob.executeGlobal ==========");
		List<Long> pendingTaskIds = findPendingTaskIds(TemuAuthRegion.GLOBAL);
		assertTrue(!pendingTaskIds.isEmpty(), "没有待执行的GLOBAL售后任务，请先执行步骤6");

		long parentBefore = temuParentAftersalesService.count();
		long childBefore = temuChildrenAftersalesService.count();
		executeJob(null, temuAftersalesPullJob::executeGlobal);
		log.info("GLOBAL售后拉取前后: parent {} -> {}, children {} -> {}", parentBefore, temuParentAftersalesService.count(),
				childBefore, temuChildrenAftersalesService.count());

		for (Long taskId : pendingTaskIds) {
			SyncTask task = temuOrderRequestTaskService.getById(taskId);
			assertTrue(task != null, "GLOBAL售后任务不存在: taskId=" + taskId);
			assertTrue(Integer.valueOf(2).equals(task.getState()), "GLOBAL售后任务执行失败: taskId=" + taskId + ", state="
					+ task.getState() + ", error=" + task.getErrorMessage());
		}
		log.info("✓ 步骤7完成: GLOBAL售后任务执行成功，数据已真实落库");
	}

	@Test
	@Order(8)
	@DisplayName("步骤8 - 验证GLOBAL售后继承父订单真实channel和site")
	void step8_verifyGlobalAftersalesOwnership() {
		log.info("========== 步骤8: 验证GLOBAL售后归属 ==========");
		long verifiedParents = 0;
		for (TemuAccessToken mapping : globalAftersalesAccounts()) {
			List<TemuParentAftersales> parents = temuParentAftersalesService.lambdaQuery()
				.eq(TemuParentAftersales::getSellerId, mapping.getSellerId())
				.eq(TemuParentAftersales::getChannelId, mapping.getChannelId())
				.eq(TemuParentAftersales::getSite, mapping.getSite())
				.orderByDesc(TemuParentAftersales::getUpdateTime)
				.list();
			log.info("GLOBAL售后映射 site={}, channelId={}, 父售后数={}", mapping.getSite(), mapping.getChannelId(),
					parents.size());

			for (TemuParentAftersales parent : parents) {
				verifiedParents++;
				TemuParentOrder parentOrder = temuParentOrderService.lambdaQuery()
					.eq(TemuParentOrder::getParentOrderSn, parent.getParentOrderSn())
					.eq(TemuParentOrder::getSellerId, mapping.getSellerId())
					.eq(TemuParentOrder::getChannelId, mapping.getChannelId())
					.eq(TemuParentOrder::getSite, mapping.getSite())
					.last("LIMIT 1")
					.one();
				assertTrue(parentOrder != null,
						"GLOBAL父售后未继承到对应父订单归属: parentAftersalesSn=" + parent.getParentAftersalesSn()
								+ ", parentOrderSn=" + parent.getParentOrderSn() + ", site=" + mapping.getSite()
								+ ", channelId=" + mapping.getChannelId());

				List<TemuChildrenAftersales> children = temuChildrenAftersalesService.lambdaQuery()
					.eq(TemuChildrenAftersales::getSellerId, mapping.getSellerId())
					.eq(TemuChildrenAftersales::getParentAftersalesSn, parent.getParentAftersalesSn())
					.list();
				assertTrue(!children.isEmpty(), "父售后缺少子售后落库: " + parent.getParentAftersalesSn());
				for (TemuChildrenAftersales child : children) {
					assertOwnership(mapping, child.getChannelId(), child.getSite(), "子售后", child.getAftersalesSn());
				}

				List<TemuAftersalesProduct> products = temuAftersalesProductService.lambdaQuery()
					.eq(TemuAftersalesProduct::getSellerId, mapping.getSellerId())
					.eq(TemuAftersalesProduct::getParentAftersalesSn, parent.getParentAftersalesSn())
					.list();
				for (TemuAftersalesProduct product : products) {
					assertOwnership(mapping, product.getChannelId(), product.getSite(), "售后商品",
							product.getAftersalesSn());
				}

				List<TemuAftersalesReturnLogistics> logistics = temuAftersalesReturnLogisticsService.lambdaQuery()
					.eq(TemuAftersalesReturnLogistics::getSellerId, mapping.getSellerId())
					.eq(TemuAftersalesReturnLogistics::getParentAftersalesSn, parent.getParentAftersalesSn())
					.list();
				for (TemuAftersalesReturnLogistics row : logistics) {
					assertOwnership(mapping, row.getChannelId(), row.getSite(), "退货物流", row.getTrackingNumber());
				}
				log.info("  parentAftersalesSn={}, parentOrderSn={}, children={}, products={}, logistics={}",
						parent.getParentAftersalesSn(), parent.getParentOrderSn(), children.size(), products.size(),
						logistics.size());
			}
		}
		assertTrue(verifiedParents > 0, "没有可验证的GLOBAL售后，请确认父订单已先拉取且测试窗口内存在售后单");
		log.info("✓ 步骤8完成: 共验证 {} 条GLOBAL父售后及关联表归属", verifiedParents);
	}

	private List<Long> findPendingTaskIds(TemuAuthRegion authRegion) {
		List<Long> taskIds = new ArrayList<>();
		List<TemuAccessToken> accounts = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getAuthRegion, authRegion.getCode())
			.eq(TemuAccessToken::getType, TemuPullType.AFTERSALES.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.list();
		for (TemuAccessToken account : uniqueRequestAccounts(accounts).values()) {
			List<SyncTask> tasks = temuOrderRequestTaskService.getUnRequestTaskByAuth(account.getAppId(),
					account.getSellerId(), account.getAuthRegion(), TemuPullType.AFTERSALES.getCode());
			for (SyncTask task : tasks) {
				if (!taskIds.contains(task.getId())) {
					taskIds.add(task.getId());
				}
			}
		}
		return taskIds;
	}

	private List<TemuAccessToken> globalAftersalesAccounts() {
		return temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getAuthRegion, TemuAuthRegion.GLOBAL.getCode())
			.eq(TemuAccessToken::getType, TemuPullType.AFTERSALES.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.orderByAsc(TemuAccessToken::getId)
			.list();
	}

	private Map<String, TemuAccessToken> uniqueRequestAccounts(List<TemuAccessToken> accounts) {
		Map<String, TemuAccessToken> result = new LinkedHashMap<>();
		for (TemuAccessToken account : accounts) {
			String key = account.getAppId() + "|" + account.getSellerId() + "|" + account.getAuthRegion() + "|"
					+ account.getType();
			result.putIfAbsent(key, account);
		}
		return result;
	}

	private void assertOwnership(TemuAccessToken expected, Integer actualChannelId, String actualSite, String rowType,
			String rowKey) {
		assertTrue(expected.getChannelId().equals(actualChannelId) && expected.getSite().equalsIgnoreCase(actualSite),
				rowType + "归属错误: key=" + rowKey + ", expectedChannelId=" + expected.getChannelId()
						+ ", actualChannelId=" + actualChannelId + ", expectedSite=" + expected.getSite()
						+ ", actualSite=" + actualSite);
	}

	private static void assertTrue(boolean condition, String msg) {
		if (!condition)
			throw new AssertionError(msg);
	}

}
