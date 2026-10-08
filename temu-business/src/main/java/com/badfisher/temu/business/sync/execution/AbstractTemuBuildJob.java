package com.badfisher.temu.business.sync.execution;

import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.business.sync.service.SyncTaskService;
import com.badfisher.temu.business.sync.enums.TemuPullType;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Temu 拉取任务构建器抽象基类 — 封装按站点遍历店铺、时间窗口决策、任务创建的公共逻辑。
 * <p>
 * 子类只需实现：
 * <ul>
 * <li>{@link #pullType()} — 任务拉取类型</li>
 * <li>{@link #defaultPullStatus()} — 首次构建的默认 pullStatus</li>
 * <li>{@link #defaultTimeType()} — 首次构建的默认 timeType</li>
 * </ul>
 * 并在子类中提供 {@code @XxlJob} 入口方法调用 {@link #buildForAuthRegion(TemuAuthRegion)}。
 *
 * @author lenkor
 */
@Slf4j
@Component
public abstract class AbstractTemuBuildJob {

	/** 首次全量同步固定从北京时间2024-01-01 00:00:00开始。 */
	private static final LocalDateTime FIRST_PULL_BEGIN_TIME = LocalDateTime.of(2024, 1, 1, 0, 0, 0);

	private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

	private static final int OVERLAP_MINUTES = 2;

	private static final long TASK_STUCK_TIMEOUT_MS = MINUTES.toMillis(10);

	@Resource
	protected TemuAccessTokenService temuAccessTokenService;

	@Resource
	protected SyncTaskService temuOrderRequestTaskService;

	/** 子类返回任务拉取类型 */
	protected abstract TemuPullType pullType();

	/** 子类返回首次构建的默认 pullStatus */
	protected abstract String defaultPullStatus();

	/** 子类返回首次构建的默认 timeType */
	protected String defaultTimeType() {
		return "lastmodifieddate";
	}

	/** 订单类任务需要ERP渠道；商品任务按appId和sellerId归档，可覆盖为false。 */
	protected boolean requiresChannelId() {
		return true;
	}

	/** 按授权区域创建任务；同一请求主体只创建一份，业务site不参与任务去重。 */
	protected void buildForAuthRegion(TemuAuthRegion authRegion) {
		log.info("====== Temu {} {}任务构建 开始 ======", authRegion, pullType().getDesc());
		Date currentDate = new Date();

		List<TemuAccessToken> accounts = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getAuthRegion, authRegion.getCode())
			.eq(TemuAccessToken::getType, pullType().getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.list();
		if (accounts.isEmpty()) {
			log.info("无 {} 授权区域启用账号，跳过{}任务构建", authRegion, pullType().getDesc());
			return;
		}

		Map<String, TemuAccessToken> requestAccounts = new LinkedHashMap<>();
		for (TemuAccessToken account : accounts) {
			String key = account.getAppId() + "|" + account.getSellerId() + "|" + account.getAuthRegion() + "|"
					+ account.getType();
			requestAccounts.merge(key, account, (a, b) -> {
				validateSameRequestAuthorization(a, b);
				Long aId = a.getId() == null ? Long.MAX_VALUE : a.getId();
				Long bId = b.getId() == null ? Long.MAX_VALUE : b.getId();
				return aId <= bId ? a : b;
			});
		}

		int createdCount = 0;
		for (TemuAccessToken account : requestAccounts.values()) {
			try {
				if (requiresChannelId() && account.getChannelId() == null) {
					log.error("店铺 {}({}) 缺少channelId，拒绝构建{}任务", account.getSellerId(), account.getSite(),
							pullType().getDesc());
					continue;
				}
				SyncTask task = buildTask(account, currentDate);
				if (task != null) {
					temuOrderRequestTaskService.save(task);
					createdCount++;
					log.debug("店铺 {} {}任务创建成功: beginTime={}, endTime={}", account.getSellerId(), pullType().getDesc(),
							task.getBeginTime(), task.getEndTime());
				}
			}
			catch (Exception e) {
				log.error("店铺 {} {}任务构建失败", account.getSellerId(), pullType().getDesc(), e);
			}
		}

		log.info("====== Temu {} {}任务构建 结束 | 请求主体数={}, 创建任务数={} ======", authRegion, pullType().getDesc(),
				requestAccounts.size(), createdCount);
	}

	private void validateSameRequestAuthorization(TemuAccessToken a, TemuAccessToken b) {
		if (!Objects.equals(a.getAppKey(), b.getAppKey()) || !Objects.equals(a.getAppSecret(), b.getAppSecret())
				|| !Objects.equals(a.getAccessToken(), b.getAccessToken())) {
			throw new IllegalStateException("同一授权区域存在不一致的请求凭证: appId=" + a.getAppId() + ", sellerId=" + a.getSellerId()
					+ ", authRegion=" + a.getAuthRegion());
		}
	}

	private SyncTask buildTask(TemuAccessToken account, Date currentDate) {
		SyncTask lastTask = temuOrderRequestTaskService.findLastTaskByAuth(account.getAppId(),
				account.getSellerId(), account.getAuthRegion(), pullType().getCode());

		// 无历史 → 首次构建
		if (lastTask == null) {
			return createTask(account, currentDate, firstPullBeginTime(), defaultPullStatus(), defaultTimeType());
		}

		switch (lastTask.getState()) {
			case 0:
				log.debug("店铺 {} 有待处理{}任务, 跳过构建", account.getSellerId(), pullType().getDesc());
				return null;
			case 1: {
				Date heartbeatDeadline = new Date(currentDate.getTime() - TASK_STUCK_TIMEOUT_MS);
				if (!temuOrderRequestTaskService.timeoutTask(lastTask.getId(), heartbeatDeadline, "执行超时，心跳租约已失效")) {
					log.debug("店铺 {} {}任务 {} 超时回收未成功，任务状态或心跳已更新", account.getSellerId(), pullType().getDesc(),
							lastTask.getId());
					return null;
				}
				log.warn("店铺 {} {}任务 {} 执行超时，已标记为失败", account.getSellerId(), pullType().getDesc(), lastTask.getId());
				return createTask(account, currentDate, lastTask.getBeginTime(), lastTask.getPullStatus(),
						lastTask.getTimeType());
			}
			case 3:
				return createTask(account, currentDate, lastTask.getBeginTime(), lastTask.getPullStatus(),
						lastTask.getTimeType());
			case 2:
				return createTask(account, currentDate,
						new Date(lastTask.getEndTime().getTime() - MINUTES.toMillis(OVERLAP_MINUTES)),
						lastTask.getPullStatus(), lastTask.getTimeType());
			default:
				log.warn("店铺 {} {}任务 {} 状态异常 state={}, 跳过构建", account.getSellerId(), pullType().getDesc(),
						lastTask.getId(), lastTask.getState());
				return null;
		}
	}

	/** 每次返回新的Date对象，避免可变Date在任务之间共享。 */
	static Date firstPullBeginTime() {
		return Date.from(FIRST_PULL_BEGIN_TIME.atZone(BUSINESS_ZONE_ID).toInstant());
	}

	private SyncTask createTask(TemuAccessToken account, Date currentDate, Date beginTime,
			String pullStatus, String timeType) {
		SyncTask task = new SyncTask();
		task.setAppId(account.getAppId());
		task.setAuthRegion(account.getAuthRegion());
		task.setBeginTime(beginTime);
		task.setEndTime(currentDate);
		task.setAccountCode(account.getSellerId());
		task.setSite(account.getSite());
		task.setChannelId(account.getChannelId());
		task.setPullType(pullType().getCode());
		task.setPullStatus(pullStatus != null ? pullStatus : defaultPullStatus());
		task.setTimeType(timeType != null ? timeType : defaultTimeType());
		task.setState(0);
		return task;
	}

}
