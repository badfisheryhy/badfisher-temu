package com.badfisher.temu.business.sync.execution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.business.sync.service.SyncTaskService;
import com.badfisher.temu.business.sync.enums.TemuPullType;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Temu 拉取任务执行器抽象基类 — 封装按站点遍历店铺、线程池并发、token 校验的公共逻辑。
 * <p>
 * 子类只需实现：
 * <ul>
 * <li>{@link #pullType()} — 任务拉取类型</li>
 * <li>{@link #doPull(TemuAccessToken, SyncTask, TemuAuthRegion)} —
 * 实际拉取委托</li>
 * </ul>
 * 并在子类中提供 {@code @XxlJob} 入口方法调用 {@link #executeForAuthRegion(TemuAuthRegion)}。
 *
 * @author lenkor
 */
@Slf4j
@Component
public abstract class AbstractTemuPullJob {

	@Resource
	protected TemuAccessTokenService temuAccessTokenService;

	@Resource
	protected SyncTaskService temuOrderRequestTaskService;

	@Resource(name = "temuScheduleExecutor")
	protected ThreadPoolTaskExecutor scheduleExecutor;

	@Resource(name = "temuTaskHeartbeatScheduler")
	private ThreadPoolTaskScheduler heartbeatScheduler;

	private static final int BATCH_SIZE = 3;

	private static final long HEARTBEAT_INTERVAL_MS = 60_000L;

	/** 子类返回任务拉取类型 */
	protected abstract TemuPullType pullType();

	/** 子类委托实际的拉取逻辑 */
	protected abstract void doPull(TemuAccessToken account, SyncTask task, TemuAuthRegion authRegion);

	/** 订单类拉取需要ERP渠道；商品任务按appId和sellerId归档，可覆盖为false。 */
	protected boolean requiresChannelId() {
		return true;
	}

	protected void executeForAuthRegion(TemuAuthRegion authRegion) {
		log.info("====== Temu {} {}拉取任务 开始 ======", authRegion.getDesc(), pullType().getDesc());

		List<TemuAccessToken> accounts = temuAccessTokenService.list(authAccountQuery(authRegion));
		if (accounts.isEmpty()) {
			log.info("无 {} 授权区域账号，跳过{}拉取", authRegion.getDesc(), pullType().getDesc());
			return;
		}

		Map<String, TemuAccessToken> requestAccountMap = new LinkedHashMap<>();
		for (TemuAccessToken account : accounts) {
			String key = account.getAppId() + "|" + account.getSellerId() + "|" + account.getAuthRegion() + "|"
					+ account.getType();
			requestAccountMap.merge(key, account, (a, b) -> {
				validateSameRequestAuthorization(a, b);
				Long aId = a.getId() == null ? Long.MAX_VALUE : a.getId();
				Long bId = b.getId() == null ? Long.MAX_VALUE : b.getId();
				return aId <= bId ? a : b;
			});
		}
		accounts = new ArrayList<>(requestAccountMap.values());

		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (int i = 0; i < accounts.size(); i += BATCH_SIZE) {
			int end = Math.min(i + BATCH_SIZE, accounts.size());
			List<TemuAccessToken> batch = accounts.subList(i, end);

			futures.add(CompletableFuture.runAsync(() -> {
				for (TemuAccessToken account : batch) {
					try {
						handleAccount(account);
					}
					catch (Exception e) {
						log.error("店铺 {} {}拉取批次处理失败", account.getSellerId(), pullType().getDesc(), e);
					}
				}
			}, scheduleExecutor));
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		log.info("====== Temu {} {}拉取任务 完成 ======", authRegion.getDesc(), pullType().getDesc());
	}

	private void validateSameRequestAuthorization(TemuAccessToken a, TemuAccessToken b) {
		if (!Objects.equals(a.getAppKey(), b.getAppKey()) || !Objects.equals(a.getAppSecret(), b.getAppSecret())
				|| !Objects.equals(a.getAccessToken(), b.getAccessToken())) {
			throw new IllegalStateException("同一授权区域存在不一致的请求凭证: appId=" + a.getAppId() + ", sellerId=" + a.getSellerId()
					+ ", authRegion=" + a.getAuthRegion());
		}
	}

	LambdaQueryWrapper<TemuAccessToken> authAccountQuery(TemuAuthRegion authRegion) {
		return Wrappers.lambdaQuery(TemuAccessToken.class)
			.eq(TemuAccessToken::getAuthRegion, authRegion.getCode())
			.eq(TemuAccessToken::getType, pullType().getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0);
	}

	private void handleAccount(TemuAccessToken account) {
		if (requiresChannelId() && account.getChannelId() == null) {
			log.error("店铺 {}({}) 缺少channelId，拒绝执行{}拉取", account.getSellerId(), account.getSite(), pullType().getDesc());
			return;
		}
		List<SyncTask> tasks = temuOrderRequestTaskService.getUnRequestTaskByAuth(account.getAppId(),
				account.getSellerId(), account.getAuthRegion(), pullType().getCode());
		if (tasks.isEmpty()) {
			return;
		}

		log.info("店铺 {} 待处理{}任务数: {}", account.getSellerId(), pullType().getDesc(), tasks.size());

		TemuAuthRegion authRegion = TemuAuthRegion.fromCode(account.getAuthRegion());
		if (authRegion == null) {
			log.warn("店铺 {} 授权区域 {} 无效, 跳过", account.getSellerId(), account.getAuthRegion());
			return;
		}

		String appKey = account.getAppKey();
		String appSecret = account.getAppSecret();
		String accessToken = account.getAccessToken();
		if (appKey == null || appSecret == null || accessToken == null) {
			log.warn("店铺 {} 授权信息不完整, 跳过拉取", account.getSellerId());
			return;
		}

		if (account.isExpired()) {
			log.warn("店铺 {} accessToken 已过期, 跳过拉取", account.getSellerId());
			return;
		}

		for (SyncTask task : tasks) {
			ScheduledFuture<?> heartbeatFuture = null;
			boolean claimed = false;
			String executionId = UUID.randomUUID().toString();
			try {
				if (!temuOrderRequestTaskService.claimTaskByAuth(task.getId(), account.getAppId(),
						account.getSellerId(), account.getAuthRegion(), pullType().getCode(), executionId)) {
					log.debug("店铺 {} 任务 {} 已被其他执行器领取，跳过", account.getSellerId(), task.getId());
					continue;
				}
				claimed = true;
				task.setExecutionId(executionId);
				task.setState(1);
				heartbeatFuture = startHeartbeat(task, account);
				doPull(account, task, authRegion);
			}
			catch (Exception e) {
				log.error("店铺 {} {}任务 {} 拉取失败", account.getSellerId(), pullType().getDesc(), task.getId(), e);
				if (claimed && !temuOrderRequestTaskService.failTask(task.getId(), executionId, e.getMessage())) {
					log.warn("店铺 {} {}任务 {} 已不属于当前执行器，忽略异常状态回写", account.getSellerId(), pullType().getDesc(),
							task.getId());
				}
			}
			finally {
				stopHeartbeat(heartbeatFuture);
			}
		}
	}

	/**
	 * 原子领取成功后启动固定频率心跳。所有基于任务表的 PullJob 自动获得续租能力。
	 */
	private ScheduledFuture<?> startHeartbeat(SyncTask task, TemuAccessToken account) {
		AtomicBoolean leaseActive = new AtomicBoolean(true);
		return heartbeatScheduler.scheduleAtFixedRate(() -> {
			if (!leaseActive.get()) {
				return;
			}
			try {
				if (!temuOrderRequestTaskService.heartbeatTask(task.getId(), task.getExecutionId())) {
					leaseActive.set(false);
					log.debug("店铺 {} {}任务 {} 心跳已停止，任务已结束或被回收", account.getSellerId(), pullType().getDesc(),
							task.getId());
				}
			}
			catch (Exception e) {
				log.error("店铺 {} {}任务 {} 心跳更新异常", account.getSellerId(), pullType().getDesc(), task.getId(), e);
			}
		}, HEARTBEAT_INTERVAL_MS);
	}

	private void stopHeartbeat(ScheduledFuture<?> heartbeatFuture) {
		if (heartbeatFuture != null) {
			heartbeatFuture.cancel(false);
		}
	}

}
