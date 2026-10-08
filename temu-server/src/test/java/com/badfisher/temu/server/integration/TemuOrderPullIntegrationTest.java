package com.badfisher.temu.server.integration;

import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.business.order.service.TemuChildrenOrderService;
import com.badfisher.temu.business.order.service.TemuOrderAddressService;
import com.badfisher.temu.business.order.service.TemuOrderProductService;
import com.badfisher.temu.business.order.service.TemuOrderPullService;
import com.badfisher.temu.business.order.service.TemuParentOrderService;
import com.badfisher.temu.business.sync.service.SyncTaskService;

import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.order.model.TemuChildrenOrder;
import com.badfisher.temu.business.order.model.TemuOrderAddress;
import com.badfisher.temu.business.order.model.TemuOrderProduct;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.order.model.TemuParentOrder;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.badfisher.temu.client.enums.TemuGlobalSite;
import com.badfisher.temu.business.sync.enums.TemuPullType;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

/**
 * Temu订单拉取Service真实链路集成测试，需要Spring上下文、DB、Redis及有效授权。 测试会创建真实任务并写入订单相关表，不回滚。
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "TEMU_LIVE_TESTS", matches = "true")
@SpringBootTest(classes = com.badfisher.temu.server.TemuApplication.class)
class TemuOrderPullIntegrationTest {

	@Resource
	private TemuOrderPullService temuOrderPullService;

	@Resource
	private TemuAccessTokenService temuAccessTokenService;

	@Resource
	private SyncTaskService temuOrderRequestTaskService;

	@Resource(name = "temuTaskHeartbeatScheduler")
	private ThreadPoolTaskScheduler heartbeatScheduler;

	@Resource
	private TemuParentOrderService temuParentOrderService;

	@Resource
	private TemuChildrenOrderService temuChildrenOrderService;

	@Resource
	private TemuOrderAddressService temuOrderAddressService;

	@Resource
	private TemuOrderProductService temuOrderProductService;

	@Test
	@DisplayName("US完整链路 - 拉取订单并验证真实落库")
	void shouldPullUsOrdersAndPersistToDb() {
		TemuAccessToken account = firstRequestAccount(TemuAuthRegion.US);
		SyncTask task = createRunningTask(account);
		long parentBefore = countParents(account);

		pullWithHeartbeat(account, task, TemuAuthRegion.US);

		assertTaskCompleted(task.getId(), "US订单");
		long parentAfter = countParents(account);
		assertTrue(parentAfter >= parentBefore, "US父订单数不应减少");
		log.info("US订单拉取完成: taskId={}, parent {} -> {}", task.getId(), parentBefore, parentAfter);
	}

	@Test
	@DisplayName("GLOBAL完整链路 - 拉取一次并按siteId写入真实channel/site")
	void shouldPullGlobalOrdersAndPersistToRealChannels() {
		TemuAccessToken requestAccount = firstRequestAccount(TemuAuthRegion.GLOBAL);
		List<TemuAccessToken> mappings = requestMappings(requestAccount);
		assertTrue(mappings.size() >= 2, "GLOBAL订单测试至少需要同一授权主体的两条业务站点映射");
		SyncTask task = createRunningTask(requestAccount);

		pullWithHeartbeat(requestAccount, task, TemuAuthRegion.GLOBAL);

		assertTaskCompleted(task.getId(), "GLOBAL订单");
		long verifiedParents = 0;
		for (TemuAccessToken mapping : mappings) {
			List<TemuParentOrder> parents = temuParentOrderService.lambdaQuery()
				.eq(TemuParentOrder::getSellerId, mapping.getSellerId())
				.eq(TemuParentOrder::getChannelId, mapping.getChannelId())
				.eq(TemuParentOrder::getSite, mapping.getSite())
				.list();
			log.info("GLOBAL订单映射 site={}, channelId={}, parent={}", mapping.getSite(), mapping.getChannelId(),
					parents.size());
			for (TemuParentOrder parent : parents) {
				verifiedParents++;
				TemuGlobalSite globalSite = TemuGlobalSite.fromSiteId(parent.getSiteId());
				assertTrue(globalSite != null, "GLOBAL订单siteId无法识别: " + parent.getSiteId());
				assertTrue(
						globalSite.getCode()
							.toUpperCase(Locale.ROOT)
							.equals(mapping.getSite().toUpperCase(Locale.ROOT)),
						"GLOBAL订单siteId与落库site不一致: parentOrderSn=" + parent.getParentOrderSn());
				verifyOrderChildrenOwnership(mapping, parent.getParentOrderSn());
			}
		}
		assertTrue(verifiedParents > 0, "没有可验证的GLOBAL订单，请确认测试窗口内存在GLOBAL订单");
		log.info("GLOBAL订单拉取及归属验证完成: taskId={}, parents={}", task.getId(), verifiedParents);
	}

	@Test
	@DisplayName("US幂等性 - 相同窗口二次拉取不产生重复父订单")
	void shouldNotProduceDuplicateOnReplay() {
		TemuAccessToken account = firstRequestAccount(TemuAuthRegion.US);
		Date endTime = new Date();
		Date beginTime = new Date(endTime.getTime() - 10L * 24 * 3600 * 1000);

		SyncTask firstTask = createRunningTask(account, beginTime, endTime);
		pullWithHeartbeat(account, firstTask, TemuAuthRegion.US);
		assertTaskCompleted(firstTask.getId(), "US订单第一次幂等拉取");
		long parentAfterFirst = countParents(account);

		SyncTask secondTask = createRunningTask(account, beginTime, endTime);
		pullWithHeartbeat(account, secondTask, TemuAuthRegion.US);
		assertTaskCompleted(secondTask.getId(), "US订单第二次幂等拉取");
		long parentAfterSecond = countParents(account);

		assertEquals(parentAfterFirst, parentAfterSecond, "相同窗口二次拉取不应产生重复父订单");
		log.info("US订单幂等性验证完成: parent {} -> {}", parentAfterFirst, parentAfterSecond);
	}

	private List<TemuAccessToken> activeAccounts(TemuAuthRegion authRegion) {
		return temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getAuthRegion, authRegion.getCode())
			.eq(TemuAccessToken::getType, TemuPullType.ORDER.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.isNotNull(TemuAccessToken::getChannelId)
			.orderByAsc(TemuAccessToken::getId)
			.list();
	}

	private TemuAccessToken firstRequestAccount(TemuAuthRegion authRegion) {
		List<TemuAccessToken> accounts = activeAccounts(authRegion);
		if (accounts.isEmpty()) {
			throw new IllegalStateException("无 " + authRegion.getCode() + " 订单授权记录");
		}
		return accounts.get(0);
	}

	private List<TemuAccessToken> requestMappings(TemuAccessToken requestAccount) {
		return temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getAppId, requestAccount.getAppId())
			.eq(TemuAccessToken::getSellerId, requestAccount.getSellerId())
			.eq(TemuAccessToken::getAuthRegion, requestAccount.getAuthRegion())
			.eq(TemuAccessToken::getType, requestAccount.getType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.isNotNull(TemuAccessToken::getChannelId)
			.orderByAsc(TemuAccessToken::getId)
			.list();
	}

	private SyncTask createRunningTask(TemuAccessToken account) {
		Date endTime = new Date();
		return createRunningTask(account, new Date(endTime.getTime() - 10L * 24 * 3600 * 1000), endTime);
	}

	private SyncTask createRunningTask(TemuAccessToken account, Date beginTime, Date endTime) {
		SyncTask task = new SyncTask();
		task.setAppId(account.getAppId());
		task.setAuthRegion(account.getAuthRegion());
		task.setSite(account.getSite());
		task.setChannelId(account.getChannelId());
		task.setAccountCode(account.getSellerId());
		task.setPullType(TemuPullType.ORDER.getCode());
		task.setTimeType("lastmodifieddate");
		task.setBeginTime(beginTime);
		task.setEndTime(endTime);
		task.setState(0);
		assertTrue(temuOrderRequestTaskService.save(task), "测试任务创建失败");
		String executionId = UUID.randomUUID().toString();
		assertTrue(temuOrderRequestTaskService.claimTaskByAuth(task.getId(), account.getAppId(),
				account.getSellerId(), account.getAuthRegion(), task.getPullType(), executionId), "测试任务领取失败");
		task.setExecutionId(executionId);
		task.setState(1);
		return task;
	}

	private void pullWithHeartbeat(TemuAccessToken account, SyncTask task, TemuAuthRegion authRegion) {
		ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(
				() -> temuOrderRequestTaskService.heartbeatTask(task.getId(), task.getExecutionId()), 60_000L);
		try {
			temuOrderPullService.doPullTask(account, task, authRegion);
		}
		finally {
			if (heartbeat != null) {
				heartbeat.cancel(false);
			}
		}
	}

	private void assertTaskCompleted(Long taskId, String label) {
		SyncTask task = temuOrderRequestTaskService.getById(taskId);
		assertTrue(task != null, label + "任务不存在: taskId=" + taskId);
		assertTrue(Integer.valueOf(2).equals(task.getState()),
				label + "任务未完成: taskId=" + taskId + ", state=" + task.getState() + ", error=" + task.getErrorMessage());
	}

	private long countParents(TemuAccessToken account) {
		return temuParentOrderService.lambdaQuery()
			.eq(TemuParentOrder::getSellerId, account.getSellerId())
			.eq(TemuParentOrder::getChannelId, account.getChannelId())
			.eq(TemuParentOrder::getSite, account.getSite())
			.count();
	}

	private void verifyOrderChildrenOwnership(TemuAccessToken mapping, String parentOrderSn) {
		List<TemuChildrenOrder> children = temuChildrenOrderService.lambdaQuery()
			.eq(TemuChildrenOrder::getParentOrderSn, parentOrderSn)
			.eq(TemuChildrenOrder::getSellerId, mapping.getSellerId())
			.list();
		for (TemuChildrenOrder child : children) {
			assertOwnership(mapping, child.getChannelId(), child.getSite(), "子订单", child.getOrderSn());
		}
		List<TemuOrderProduct> products = temuOrderProductService.lambdaQuery()
			.eq(TemuOrderProduct::getParentOrderSn, parentOrderSn)
			.eq(TemuOrderProduct::getSellerId, mapping.getSellerId())
			.list();
		for (TemuOrderProduct product : products) {
			assertOwnership(mapping, product.getChannelId(), product.getSite(), "订单商品",
					String.valueOf(product.getProductSkuId()));
		}
		List<TemuOrderAddress> addresses = temuOrderAddressService.lambdaQuery()
			.eq(TemuOrderAddress::getParentOrderSn, parentOrderSn)
			.eq(TemuOrderAddress::getSellerId, mapping.getSellerId())
			.list();
		for (TemuOrderAddress address : addresses) {
			assertOwnership(mapping, address.getChannelId(), address.getSite(), "订单地址", address.getParentOrderSn());
		}
	}

	private void assertOwnership(TemuAccessToken expected, Integer actualChannelId, String actualSite, String rowType,
			String rowKey) {
		assertTrue(expected.getChannelId().equals(actualChannelId) && expected.getSite().equalsIgnoreCase(actualSite),
				rowType + "归属错误: key=" + rowKey + ", expectedChannelId=" + expected.getChannelId()
						+ ", actualChannelId=" + actualChannelId + ", expectedSite=" + expected.getSite()
						+ ", actualSite=" + actualSite);
	}

	private static void assertEquals(long expected, long actual, String message) {
		if (expected != actual) {
			throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
		}
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

}
