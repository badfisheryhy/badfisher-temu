package com.badfisher.temu.server.integration;

import com.badfisher.temu.scheduler.xxljob.order.TemuOrderBuildJob;
import com.badfisher.temu.scheduler.xxljob.order.TemuOrderPullJob;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.order.model.TemuChildrenOrder;
import com.badfisher.temu.business.order.model.TemuOrderAddress;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.order.model.TemuOrderProduct;
import com.badfisher.temu.business.order.model.TemuParentOrder;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.business.order.service.TemuChildrenOrderService;
import com.badfisher.temu.business.order.service.TemuOrderAddressService;
import com.badfisher.temu.business.order.service.TemuOrderProductService;
import com.badfisher.temu.business.sync.service.SyncTaskService;
import com.badfisher.temu.business.order.service.TemuParentOrderService;
import com.badfisher.temu.business.sync.enums.TemuPullType;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.badfisher.temu.client.enums.TemuGlobalSite;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import static com.badfisher.temu.scheduler.xxljob.XxlJobTestSupport.executeJob;

/**
 * TemuOrderBuildJob + TemuOrderPullJob 完整链路集成测试。
 * <p>
 * 验证链路： 1. TemuOrderBuildJob.executeUs → 为每个 US 站点店铺创建拉取任务(state=0) 2.
 * TemuOrderPullJob.executeUs → 消费任务, 调 API 拉单, 写入 DB 3.
 * TemuOrderBuildJob/TemuOrderPullJob.executeGlobal → 每个Global授权主体只拉一次，
 * 再按siteId将CA/AU/UK订单写入真实channel
 * <p>
 * <b>数据落库, 不回滚。</b> 测试产生的 task / order / children / address / product 全部保留在 DB 中, 供人工查看。
 * <p>
 * <b>前置条件：</b>
 * <ol>
 * <li>数据库已执行 schema.sql 建表</li>
 * <li>tb_temu_access_token 已手工插入测试店铺记录(auth_region=US/GLOBAL, del_flag=0,
 * app_key/app_secret/access_token/seller_id 填真实值)</li>
 * <li>Redis 可达（限流器依赖）</li>
 * <li>通过环境变量提供测试数据库连接</li>
 * </ol>
 *
 * @author lenkor
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "TEMU_LIVE_TESTS", matches = "true")
@SpringBootTest(classes = com.badfisher.temu.server.TemuApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemuOrderPullJobIntegrationTest {

	@Resource
	private TemuOrderBuildJob temuOrderBuildJob;

	@Resource
	private TemuOrderPullJob temuOrderPullJob;

	@Resource
	private TemuAccessTokenService temuAccessTokenService;

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

	// ==================== 1. 构建拉取任务 ====================

	@Test
	@Order(1)
	@DisplayName("步骤1 - TemuOrderBuildJob 创建拉取任务")
	void step1_buildTask() {
		log.info("========== 步骤1: TemuOrderBuildJob.executeUs ==========");

		// 确认 DB 中有 US 站点测试店铺
		List<TemuAccessToken> accounts = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getAuthRegion, TemuAuthRegion.US.getCode())
			.eq(TemuAccessToken::getType, TemuPullType.ORDER.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.list();
		log.info("DB 中 US 站点店铺数: {}", accounts.size());
		for (TemuAccessToken acc : accounts) {
			log.info("  sellerId={}, shopName={}, appKey={}...", acc.getSellerId(), acc.getShopName(),
					acc.getAppKey() != null ? acc.getAppKey().substring(0, 8) : "(空)");
		}
		if (accounts.isEmpty()) {
			throw new IllegalStateException("tb_temu_access_token 中无 US 站点记录, 请先手工插入测试店铺数据");
		}

		// 拉取前 task 数量
		long taskBefore = temuOrderRequestTaskService.count();
		log.info("构建前 task 总数: {}", taskBefore);

		// 触发 BuildJob
		executeJob(null, temuOrderBuildJob::executeUs);

		// 验证 task 已创建
		long taskAfter = temuOrderRequestTaskService.count();
		log.info("构建后 task 总数: {}", taskAfter);
		assertTrue(taskAfter > taskBefore, "BuildJob 应创建了新任务, before=" + taskBefore + ", after=" + taskAfter);

		// 查看待处理任务
		for (TemuAccessToken acc : accounts) {
			List<SyncTask> tasks = temuOrderRequestTaskService.getUnRequestTaskByAuth(acc.getAppId(),
					acc.getSellerId(), acc.getAuthRegion(), TemuPullType.ORDER.getCode());
			log.info("店铺 {} 待处理任务(state=0)数: {}", acc.getSellerId(), tasks.size());
			for (SyncTask t : tasks) {
				log.info("  taskId={}, beginTime={}, endTime={}, state={}", t.getId(), t.getBeginTime(), t.getEndTime(),
						t.getState());
			}
		}
		log.info("✓ 步骤1完成: 拉取任务已创建并落库");
	}

	// ==================== 2. 执行拉单 ====================

	@Test
	@Order(2)
	@DisplayName("步骤2 - TemuOrderPullJob 拉取订单落库")
	void step2_pullOrders() {
		log.info("========== 步骤2: TemuOrderPullJob.executeUs ==========");

		// 拉取前各表数据量
		List<TemuAccessToken> accounts = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getAuthRegion, TemuAuthRegion.US.getCode())
			.eq(TemuAccessToken::getDelFlag, 0)
			.list();

		log.info("拉取前 DB 数据量:");
		for (TemuAccessToken acc : accounts) {
			log.info("  sellerId={}: parent={}, children={}, address={}", acc.getSellerId(),
					countParent(acc.getSellerId()), countChildren(acc.getSellerId()), countAddress(acc.getSellerId()));
		}
		log.info("  product(全表)={}", temuOrderProductService.count());

		// 触发 PullJob
		executeJob(null, temuOrderPullJob::executeUs);

		// 拉取后各表数据量
		log.info("拉取后 DB 数据量:");
		for (TemuAccessToken acc : accounts) {
			log.info("  sellerId={}: parent={}, children={}, address={}", acc.getSellerId(),
					countParent(acc.getSellerId()), countChildren(acc.getSellerId()), countAddress(acc.getSellerId()));
		}
		log.info("  product(全表)={}", temuOrderProductService.count());

		// 验证 task 状态
		for (TemuAccessToken acc : accounts) {
			SyncTask lastTask = temuOrderRequestTaskService.findLastTaskByAuth(acc.getAppId(),
					acc.getSellerId(), acc.getAuthRegion(), TemuPullType.ORDER.getCode());
			if (lastTask != null) {
				log.info("店铺 {} 最后任务: id={}, state={}(2=完成,3=失败), error={}", acc.getSellerId(), lastTask.getId(),
						lastTask.getState(), lastTask.getErrorMessage());
			}
		}
		log.info("✓ 步骤2完成: 拉单执行完毕, 数据已落库");
	}

	// ==================== 3. 验证落库数据 ====================

	@Test
	@Order(3)
	@DisplayName("步骤3 - 验证订单数据完整落库")
	void step3_verifyPersistedData() {
		log.info("========== 步骤3: 验证落库数据 ==========");

		List<TemuAccessToken> accounts = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getAuthRegion, TemuAuthRegion.US.getCode())
			.eq(TemuAccessToken::getDelFlag, 0)
			.list();

		for (TemuAccessToken acc : accounts) {
			String sellerId = acc.getSellerId();
			log.info("------ 店铺 {} ------", sellerId);

			// 父订单
			List<TemuParentOrder> parents = temuParentOrderService.lambdaQuery()
				.eq(TemuParentOrder::getSellerId, sellerId)
				.orderByDesc(TemuParentOrder::getCreateTime)
				.last("LIMIT 10")
				.list();
			log.info("父订单(最近10条): {}", parents.size());
			for (TemuParentOrder p : parents) {
				log.info("  id={}, parentOrderSn={}, status={}, buyerName={}, orderTime={}", p.getId(),
						p.getParentOrderSn(), p.getParentOrderStatus(), p.getBuyerName(), p.getParentOrderTime());

				// 每个父订单的子订单
				List<TemuChildrenOrder> children = temuChildrenOrderService.lambdaQuery()
					.eq(TemuChildrenOrder::getParentOrderSn, p.getParentOrderSn())
					.eq(TemuChildrenOrder::getSellerId, sellerId)
					.list();
				log.info("    子订单数: {}", children.size());
				for (TemuChildrenOrder c : children) {
					log.info("      orderSn={}, goodsName={}, status={}, qty={}", c.getOrderSn(), c.getGoodsName(),
							c.getOrderStatus(), c.getQuantity());
				}

				// 地址
				TemuOrderAddress addr = temuOrderAddressService.lambdaQuery()
					.eq(TemuOrderAddress::getParentOrderSn, p.getParentOrderSn())
					.eq(TemuOrderAddress::getSellerId, sellerId)
					.one();
				if (addr != null) {
					log.info("    地址: receiptName={}, countryCode={}, phone={}", addr.getReceiptName(),
							addr.getCountryCode(), addr.getMobile());
				}
				else {
					log.info("    地址: (无)");
				}
			}

			// 汇总
			log.info("店铺 {} 汇总: parent={}, children={}, address={}", sellerId, countParent(sellerId),
					countChildren(sellerId), countAddress(sellerId));
		}

		log.info("✓ 步骤3完成: 数据验证完毕, 全部保留在 DB 中");
	}

	// ==================== 4. GLOBAL 构建任务 ====================

	@Test
	@Order(4)
	@DisplayName("步骤4 - GLOBAL授权区按请求主体创建唯一订单任务")
	void step4_buildGlobalTask() {
		log.info("========== 步骤4: TemuOrderBuildJob.executeGlobal ==========");
		List<TemuAccessToken> mappings = globalOrderAccounts();
		if (mappings.isEmpty()) {
			throw new IllegalStateException("无 auth_region=GLOBAL 的订单授权记录");
		}

		Map<String, TemuAccessToken> requestAccounts = uniqueRequestAccounts(mappings);
		log.info("GLOBAL区域映射数={}, 唯一请求主体数={}", mappings.size(), requestAccounts.size());
		for (TemuAccessToken mapping : mappings) {
			log.info("  appId={}, sellerId={}, site={}, channelId={}, authRegion={}", mapping.getAppId(),
					mapping.getSellerId(), mapping.getSite(), mapping.getChannelId(), mapping.getAuthRegion());
		}
		validateGlobalMappingGroups(mappings);
		assertTrue(mappings.size() > requestAccounts.size(),
				"GLOBAL测试数据至少需要一个请求身份对应多条区域映射，" + "当前映射数=" + mappings.size() + ", 请求主体数=" + requestAccounts.size());

		executeJob(null, temuOrderBuildJob::executeGlobal);

		for (TemuAccessToken requestAccount : requestAccounts.values()) {
			SyncTask lastTask = temuOrderRequestTaskService.findLastTaskByAuth(requestAccount.getAppId(),
					requestAccount.getSellerId(), TemuAuthRegion.GLOBAL.getCode(), TemuPullType.ORDER.getCode());
			assertTrue(lastTask != null, "GLOBAL BuildJob未创建/找到任务: appId=" + requestAccount.getAppId() + ", sellerId="
					+ requestAccount.getSellerId());
			assertTrue(Integer.valueOf(0).equals(lastTask.getState()),
					"GLOBAL任务构建后应为state=0，taskId=" + lastTask.getId() + ", state=" + lastTask.getState());
			log.info("  GLOBAL taskId={}, appId={}, sellerId={}, window=[{} - {}], state={}", lastTask.getId(),
					lastTask.getAppId(), lastTask.getAccountCode(), lastTask.getBeginTime(), lastTask.getEndTime(),
					lastTask.getState());
		}
		log.info("✓ 步骤4完成: GLOBAL任务已按请求主体去重创建");
	}

	// ==================== 5. GLOBAL 拉单 ====================

	@Test
	@Order(5)
	@DisplayName("步骤5 - GLOBAL订单拉取并按siteId分流")
	void step5_pullGlobalOrders() {
		log.info("========== 步骤5: TemuOrderPullJob.executeGlobal ==========");
		Map<String, TemuAccessToken> requestAccounts = uniqueRequestAccounts(globalOrderAccounts());
		if (requestAccounts.isEmpty()) {
			throw new IllegalStateException("无 auth_region=GLOBAL 的订单授权记录");
		}

		executeJob(null, temuOrderPullJob::executeGlobal);

		for (TemuAccessToken requestAccount : requestAccounts.values()) {
			SyncTask lastTask = temuOrderRequestTaskService.findLastTaskByAuth(requestAccount.getAppId(),
					requestAccount.getSellerId(), TemuAuthRegion.GLOBAL.getCode(), TemuPullType.ORDER.getCode());
			assertTrue(lastTask != null, "GLOBAL拉取后找不到任务");
			assertTrue(Integer.valueOf(2).equals(lastTask.getState()), "GLOBAL订单任务执行失败: taskId=" + lastTask.getId()
					+ ", state=" + lastTask.getState() + ", error=" + lastTask.getErrorMessage());
			log.info("  GLOBAL taskId={} 已完成", lastTask.getId());
		}
		log.info("✓ 步骤5完成: GLOBAL订单任务执行成功");
	}

	// ==================== 6. GLOBAL 归属校验 ====================

	@Test
	@Order(6)
	@DisplayName("步骤6 - 验证GLOBAL订单按siteId写入真实channel")
	void step6_verifyGlobalOwnership() {
		log.info("========== 步骤6: 验证GLOBAL订单归属 ==========");
		List<TemuAccessToken> mappings = globalOrderAccounts();
		long verifiedParents = 0;

		for (TemuAccessToken mapping : mappings) {
			List<TemuParentOrder> parents = temuParentOrderService.lambdaQuery()
				.eq(TemuParentOrder::getSellerId, mapping.getSellerId())
				.eq(TemuParentOrder::getChannelId, mapping.getChannelId())
				.eq(TemuParentOrder::getSite, mapping.getSite())
				.orderByDesc(TemuParentOrder::getUpdateTime)
				.last("LIMIT 20")
				.list();
			log.info("GLOBAL映射 site={}, channelId={} 最近父订单数={}", mapping.getSite(), mapping.getChannelId(),
					parents.size());

			for (TemuParentOrder parent : parents) {
				verifiedParents++;
				TemuGlobalSite globalSite = TemuGlobalSite.fromSiteId(parent.getSiteId());
				assertTrue(globalSite != null,
						"订单siteId无法识别: parentOrderSn=" + parent.getParentOrderSn() + ", siteId=" + parent.getSiteId());
				assertTrue(
						globalSite.getCode()
							.toUpperCase(Locale.ROOT)
							.equals(mapping.getSite().toUpperCase(Locale.ROOT)),
						"订单siteId与落库site不一致: parentOrderSn=" + parent.getParentOrderSn() + ", siteId="
								+ parent.getSiteId() + ", dbSite=" + parent.getSite());

				List<TemuChildrenOrder> children = temuChildrenOrderService.lambdaQuery()
					.eq(TemuChildrenOrder::getParentOrderSn, parent.getParentOrderSn())
					.eq(TemuChildrenOrder::getChannelId, mapping.getChannelId())
					.eq(TemuChildrenOrder::getSite, mapping.getSite())
					.list();
				List<TemuOrderProduct> products = temuOrderProductService.lambdaQuery()
					.eq(TemuOrderProduct::getParentOrderSn, parent.getParentOrderSn())
					.eq(TemuOrderProduct::getChannelId, mapping.getChannelId())
					.eq(TemuOrderProduct::getSite, mapping.getSite())
					.list();
				List<TemuOrderAddress> addresses = temuOrderAddressService.lambdaQuery()
					.eq(TemuOrderAddress::getParentOrderSn, parent.getParentOrderSn())
					.eq(TemuOrderAddress::getChannelId, mapping.getChannelId())
					.eq(TemuOrderAddress::getSite, mapping.getSite())
					.list();
				log.info("  parentOrderSn={}, siteId={}, children={}, products={}, addresses={}",
						parent.getParentOrderSn(), parent.getSiteId(), children.size(), products.size(),
						addresses.size());
			}
		}

		assertTrue(verifiedParents > 0, "没有可验证的GLOBAL订单，请确认测试时间窗内存在CA/AU/UK订单");
		log.info("✓ 步骤6完成: 共验证 {} 条GLOBAL父订单归属", verifiedParents);
	}

	// ==================== 统计辅助 ====================

	private List<TemuAccessToken> globalOrderAccounts() {
		return temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getAuthRegion, TemuAuthRegion.GLOBAL.getCode())
			.eq(TemuAccessToken::getType, TemuPullType.ORDER.getAccountType())
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

	private void validateGlobalMappingGroups(List<TemuAccessToken> mappings) {
		Map<String, TemuAccessToken> requestAccountByKey = new LinkedHashMap<>();
		Map<String, Set<String>> sitesByKey = new LinkedHashMap<>();
		for (TemuAccessToken mapping : mappings) {
			String key = mapping.getAppId() + "|" + mapping.getSellerId() + "|" + mapping.getAuthRegion() + "|"
					+ mapping.getType();
			TemuAccessToken requestAccount = requestAccountByKey.putIfAbsent(key, mapping);
			if (requestAccount != null) {
				assertTrue(
						equalsText(requestAccount.getAppKey(), mapping.getAppKey())
								&& equalsText(requestAccount.getAppSecret(), mapping.getAppSecret())
								&& equalsText(requestAccount.getAccessToken(), mapping.getAccessToken()),
						"同一GLOBAL请求身份的授权凭证不一致: " + key);
			}
			assertTrue(mapping.getChannelId() != null, "GLOBAL区域映射缺少channelId: " + key + ", site=" + mapping.getSite());
			Set<String> sites = sitesByKey.computeIfAbsent(key, ignored -> new HashSet<>());
			assertTrue(sites.add(mapping.getSite().toUpperCase(Locale.ROOT)),
					"同一GLOBAL请求身份存在重复site映射: " + key + ", site=" + mapping.getSite());
		}
		for (Map.Entry<String, Set<String>> entry : sitesByKey.entrySet()) {
			log.info("  GLOBAL请求身份={}，业务站点映射={}", entry.getKey(), entry.getValue());
		}
	}

	private boolean equalsText(String a, String b) {
		return a == null ? b == null : a.equals(b);
	}

	private long countParent(String sellerId) {
		return temuParentOrderService.lambdaQuery().eq(TemuParentOrder::getSellerId, sellerId).count();
	}

	private long countChildren(String sellerId) {
		return temuChildrenOrderService.lambdaQuery().eq(TemuChildrenOrder::getSellerId, sellerId).count();
	}

	private long countAddress(String sellerId) {
		return temuOrderAddressService.lambdaQuery().eq(TemuOrderAddress::getSellerId, sellerId).count();
	}

	private static void assertTrue(boolean condition, String msg) {
		if (!condition)
			throw new AssertionError(msg);
	}

}
