package com.badfisher.temu.scheduler.xxljob.compensation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.business.aftersales.service.TemuAftersalesPullService;
import com.badfisher.temu.business.order.service.TemuOrderAmountPullService;
import com.badfisher.temu.business.order.service.TemuOrderPullService;
import com.badfisher.temu.business.sync.service.SyncTaskService;
import com.badfisher.temu.business.product.service.TemuProductPullService;
import com.badfisher.temu.business.sync.enums.TemuPullType;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.badfisher.temu.scheduler.xxljob.TemuXxlJobResult.run;

/**
 * Temu失败任务手工重跑Job。
 * <p>
 * XXL-Job参数为任务ID，支持单个ID或逗号分隔的多个ID，例如： {@code 123}、{@code 123,124}。仅允许state=3的任务被原子重新领取，
 * 重跑复用原任务时间窗口及现有幂等落库逻辑，用于失败恢复和历史数据修复。
 *
 * @author lenkor
 */
@Slf4j
@Component
public class TemuFailedTaskRetryJob {

	private static final int MAX_TASKS_PER_RUN = 100;

	private static final long HEARTBEAT_INTERVAL_MS = 60_000L;

	@Resource
	private SyncTaskService temuOrderRequestTaskService;

	@Resource
	private TemuAccessTokenService temuAccessTokenService;

	@Resource
	private TemuOrderPullService temuOrderPullService;

	@Resource
	private TemuAftersalesPullService temuAftersalesPullService;

	@Resource
	private TemuProductPullService temuProductPullService;

	@Resource
	private TemuOrderAmountPullService temuOrderAmountPullService;

	@Resource(name = "temuTaskHeartbeatScheduler")
	private ThreadPoolTaskScheduler heartbeatScheduler;

	/**
	 * 失败任务重跑入口。
	 * 调度参数为失败任务ID，多个ID使用英文逗号分隔。
	 */
	@XxlJob("temuFailedTaskRetryJob")
	public void retryFailedTask() {
		String param = XxlJobHelper.getJobParam();
		run("Temu失败任务重跑", () -> retryFailedTasks(param));
	}

	private void retryFailedTasks(String param) {
		List<Long> taskIds = parseTaskIds(param);
		List<String> failures = new ArrayList<>();
		int successCount = 0;

		log.info("====== Temu失败任务重跑开始 | taskIds={} ======", taskIds);
		for (Long taskId : taskIds) {
			try {
				retryOne(taskId);
				successCount++;
			}
			catch (Exception e) {
				String errorMessage = normalizeMessage(e);
				failures.add("taskId=" + taskId + ": " + errorMessage);
				log.error("Temu失败任务重跑失败: taskId={}, error={}", taskId, errorMessage, e);
			}
		}

		log.info("====== Temu失败任务重跑结束 | success={}, failed={} ======", successCount, failures.size());
		if (!failures.isEmpty()) {
			throw new IllegalStateException("失败任务补偿未全部成功: " + String.join("; ", failures));
		}
	}

	private void retryOne(Long taskId) {
		SyncTask task = temuOrderRequestTaskService.getById(taskId);
		if (task == null) {
			throw new IllegalArgumentException("任务不存在或已删除");
		}
		if (!Integer.valueOf(3).equals(task.getState())) {
			throw new IllegalStateException("仅允许重跑失败任务(state=3)，当前state=" + task.getState());
		}

		TemuPullType pullType = TemuPullType.fromCode(task.getPullType());
		if (pullType == null) {
			throw new IllegalStateException("不支持的pullType=" + task.getPullType());
		}
		if (pullType == TemuPullType.STOCK) {
			throw new IllegalStateException("库存使用快照Job，不属于任务表补偿范围");
		}

		TemuAuthRegion authRegion = TemuAuthRegion.fromCode(task.getAuthRegion());
		if (authRegion == null) {
			authRegion = TemuAuthRegion.fromBusinessSite(task.getSite());
		}
		if (authRegion == null) {
			throw new IllegalStateException(
					"任务授权区域无效: authRegion=" + task.getAuthRegion() + ", legacySite=" + task.getSite());
		}
		if (pullType == TemuPullType.ORDER_AMOUNT && authRegion != TemuAuthRegion.US) {
			throw new IllegalStateException("订单金额接口暂不支持授权区域: " + authRegion.getCode());
		}

		TemuAccessToken account = resolveAccount(task, pullType);
		validateAccount(account, pullType);
		backfillLegacyChannelId(task, account, pullType);

		String executionId = UUID.randomUUID().toString();
		if (!temuOrderRequestTaskService.retryFailedTask(taskId, executionId)) {
			throw new IllegalStateException("任务已被其他执行器领取或状态已变化");
		}
		task.setExecutionId(executionId);
		task.setState(1);
		task.setErrorMessage(null);

		ScheduledFuture<?> heartbeat = null;
		try {
			heartbeat = startHeartbeat(task);
			dispatch(account, task, authRegion, pullType);
			SyncTask result = temuOrderRequestTaskService.getById(taskId);
			if (result == null || !executionId.equals(result.getExecutionId())
					|| !Integer.valueOf(2).equals(result.getState())) {
				throw new IllegalStateException("本次任务执行未完成或执行身份已变化: taskId=" + taskId);
			}
		}
		catch (Exception e) {
			// 仅成功领取后的本次执行可以回写；校验失败和竞争领取失败只报告调度错误。
			temuOrderRequestTaskService.failTask(taskId, executionId, normalizeMessage(e));
			throw e;
		}
		finally {
			if (heartbeat != null) {
				heartbeat.cancel(false);
			}
		}

		log.info("失败任务重跑成功: taskId={}, channelId={}, sellerId={}, site={}, pullType={}, window=[{} - {}]", taskId,
				account.getChannelId(), account.getSellerId(), account.getSite(), pullType.getCode(),
				task.getBeginTime(), task.getEndTime());
	}

	private TemuAccessToken resolveAccount(SyncTask task, TemuPullType pullType) {
		boolean newIdentity = task.getAppId() != null && task.getAuthRegion() != null;
		List<TemuAccessToken> accounts = temuAccessTokenService.list(Wrappers.lambdaQuery(TemuAccessToken.class)
			.eq(newIdentity, TemuAccessToken::getAppId, task.getAppId())
			.eq(TemuAccessToken::getSellerId, task.getAccountCode())
			.eq(newIdentity, TemuAccessToken::getAuthRegion, task.getAuthRegion())
			.eq(!newIdentity, TemuAccessToken::getSite, task.getSite())
			.eq(TemuAccessToken::getType, pullType.getAccountType())
			.eq(!newIdentity && task.getChannelId() != null, TemuAccessToken::getChannelId, task.getChannelId())
			.eq(TemuAccessToken::getDelFlag, 0));
		if (accounts.isEmpty()) {
			throw new IllegalStateException(
					"未找到任务对应授权: channelId=" + task.getChannelId() + ", sellerId=" + task.getAccountCode() + ", site="
							+ task.getSite() + ", accountType=" + pullType.getAccountType());
		}
		if (!newIdentity && accounts.size() > 1 && pullType != TemuPullType.PRODUCT) {
			throw new IllegalStateException("任务对应多条授权，无法确定ERP店铺: sellerId=" + task.getAccountCode() + ", site="
					+ task.getSite() + ", accountType=" + pullType.getAccountType());
		}
		if (pullType == TemuPullType.PRODUCT && accounts.size() > 1) {
			TemuAccessToken first = accounts.get(0);
			for (TemuAccessToken account : accounts) {
				if (!Objects.equals(first.getAppKey(), account.getAppKey())
						|| !Objects.equals(first.getAppSecret(), account.getAppSecret())
						|| !Objects.equals(first.getAccessToken(), account.getAccessToken())) {
					throw new IllegalStateException("同一商品授权主体存在不一致的请求凭证: sellerId=" + task.getAccountCode());
				}
			}
		}
		accounts.sort((a, b) -> Long.compare(a.getId() == null ? Long.MAX_VALUE : a.getId(),
				b.getId() == null ? Long.MAX_VALUE : b.getId()));
		return accounts.get(0);
	}

	private void backfillLegacyChannelId(SyncTask task, TemuAccessToken account, TemuPullType pullType) {
		if (pullType == TemuPullType.PRODUCT || task.getChannelId() != null) {
			return;
		}
		if (account.getChannelId() == null) {
			throw new IllegalStateException("授权记录缺少channelId，无法补偿旧任务");
		}
		boolean updated = temuOrderRequestTaskService.update(Wrappers.lambdaUpdate(SyncTask.class)
			.set(SyncTask::getChannelId, account.getChannelId())
			.eq(SyncTask::getId, task.getId())
			.eq(SyncTask::getState, 3)
			.isNull(SyncTask::getChannelId));
		if (!updated) {
			throw new IllegalStateException("旧任务channelId回填失败，任务状态可能已变化");
		}
		task.setChannelId(account.getChannelId());
	}

	private void validateAccount(TemuAccessToken account, TemuPullType pullType) {
		if (pullType != TemuPullType.PRODUCT && account.getChannelId() == null) {
			throw new IllegalStateException("授权记录缺少channelId");
		}
		if (account.getAppKey() == null || account.getAppSecret() == null || account.getAccessToken() == null) {
			throw new IllegalStateException("授权信息不完整");
		}
		if (account.isExpired()) {
			throw new IllegalStateException("授权Token已过期");
		}
	}

	private void dispatch(TemuAccessToken account, SyncTask task, TemuAuthRegion authRegion,
			TemuPullType pullType) {
		switch (pullType) {
			case ORDER:
				temuOrderPullService.doPullTask(account, task, authRegion);
				break;
			case AFTERSALES:
				temuAftersalesPullService.doPullTask(account, task, authRegion);
				break;
			case PRODUCT:
				temuProductPullService.doPullTask(account, task, authRegion);
				break;
			case ORDER_AMOUNT:
				temuOrderAmountPullService.doPullTask(account, task, authRegion);
				break;
			default:
				throw new IllegalStateException("暂不支持补偿pullType=" + pullType.getCode());
		}
	}

	private ScheduledFuture<?> startHeartbeat(SyncTask task) {
		Long taskId = task.getId();
		AtomicBoolean leaseActive = new AtomicBoolean(true);
		return heartbeatScheduler.scheduleAtFixedRate(() -> {
			if (!leaseActive.get()) {
				return;
			}
			try {
				if (!temuOrderRequestTaskService.heartbeatTask(taskId, task.getExecutionId())) {
					leaseActive.set(false);
					log.debug("补偿任务心跳停止，任务已结束: taskId={}", taskId);
				}
			}
			catch (Exception e) {
				log.error("补偿任务心跳异常: taskId={}", taskId, e);
			}
		}, HEARTBEAT_INTERVAL_MS);
	}

	static List<Long> parseTaskIds(String param) {
		if (param == null || param.trim().isEmpty()) {
			throw new IllegalArgumentException("请传入失败任务ID，例如: 123 或 123,124");
		}
		Set<Long> taskIds = new LinkedHashSet<>();
		for (String value : param.split(",")) {
			String text = value.trim();
			if (text.isEmpty()) {
				continue;
			}
			try {
				long taskId = Long.parseLong(text);
				if (taskId <= 0) {
					throw new NumberFormatException("非正数");
				}
				taskIds.add(taskId);
			}
			catch (NumberFormatException e) {
				throw new IllegalArgumentException("任务ID格式错误: " + text);
			}
		}
		if (taskIds.isEmpty()) {
			throw new IllegalArgumentException("未解析到有效任务ID");
		}
		if (taskIds.size() > MAX_TASKS_PER_RUN) {
			throw new IllegalArgumentException("单次最多补偿" + MAX_TASKS_PER_RUN + "个任务");
		}
		return new ArrayList<>(taskIds);
	}

	private static String normalizeMessage(Exception e) {
		String message = e.getMessage();
		return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
	}

}
