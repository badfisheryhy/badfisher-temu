package com.badfisher.temu.business.sync.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.badfisher.temu.business.sync.execution.TaskLeaseLostException;
import com.badfisher.temu.business.sync.mapper.SyncTaskMapper;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.sync.service.SyncTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * Temu 订单请求任务 服务实现
 *
 * @author lenkor
 */
@Service
public class SyncTaskServiceImpl extends ServiceImpl<SyncTaskMapper, SyncTask>
		implements SyncTaskService {

	private static final int LEASE_SECONDS = 600;

	@Resource
	private SyncTaskMapper temuOrderRequestTaskMapper;

	@Override
	public SyncTask findLastTaskByAuth(String appId, String accountCode, String authRegion,
			String pullType) {
		return temuOrderRequestTaskMapper.findLastTaskByAuth(appId, accountCode, authRegion, pullType);
	}

	@Override
	public List<SyncTask> getUnRequestTaskByAuth(String appId, String accountCode, String authRegion,
			String pullType) {
		return temuOrderRequestTaskMapper.getUnRequestTaskByAuth(appId, accountCode, authRegion, pullType);
	}

	@Override
	public boolean claimTaskByAuth(Long taskId, String appId, String accountCode, String authRegion, String pullType,
			String executionId) {
		validateExecutionId(executionId);
		return temuOrderRequestTaskMapper.claimTaskByAuth(taskId, appId, accountCode, authRegion, pullType,
				executionId, LEASE_SECONDS) == 1;
	}

	@Override
	public SyncTask findLastTaskByAccountCode(Integer channelId, String accountCode, String site,
			String pullType) {
		return temuOrderRequestTaskMapper.findLastTaskByAccountCode(channelId, accountCode, site, pullType);
	}

	@Override
	public List<SyncTask> getUnRequestTask(Integer channelId, String accountCode, String site,
			String pullType) {
		return temuOrderRequestTaskMapper.getUnRequestTask(channelId, accountCode, site, pullType);
	}

	@Override
	public boolean claimTask(Long taskId, Integer channelId, String accountCode, String site, String pullType,
			String executionId) {
		validateExecutionId(executionId);
		return temuOrderRequestTaskMapper.claimTask(taskId, channelId, accountCode, site, pullType, executionId,
				LEASE_SECONDS) == 1;
	}

	@Override
	public boolean retryFailedTask(Long taskId, String executionId) {
		validateExecutionId(executionId);
		return temuOrderRequestTaskMapper.retryFailedTask(taskId, executionId, LEASE_SECONDS) == 1;
	}

	@Override
	public boolean heartbeatTask(Long taskId, String executionId) {
		validateExecutionId(executionId);
		return temuOrderRequestTaskMapper.heartbeatTask(taskId, executionId, LEASE_SECONDS) == 1;
	}

	@Override
	public boolean completeTask(Long taskId, String executionId) {
		validateExecutionId(executionId);
		return temuOrderRequestTaskMapper.completeTask(taskId, executionId) == 1;
	}

	@Override
	public boolean failTask(Long taskId, String executionId, String errorMessage) {
		validateExecutionId(executionId);
		return temuOrderRequestTaskMapper.failTask(taskId, executionId, normalizeErrorMessage(errorMessage)) == 1;
	}

	@Override
	public void checkExecution(SyncTask task) {
		verifyExecution(task, false);
	}

	@Override
	public void lockExecution(SyncTask task) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("执行身份锁必须与业务写入处于同一事务");
		}
		verifyExecution(task, true);
	}

	private void verifyExecution(SyncTask task, boolean lock) {
		if (task == null || task.getId() == null || !StringUtils.hasText(task.getExecutionId())) {
			throw new TaskLeaseLostException("任务缺少执行身份，拒绝继续执行");
		}
		if (Thread.currentThread().isInterrupted()) {
			throw new TaskLeaseLostException("任务执行线程已中断: taskId=" + task.getId());
		}
		Long activeTaskId;
		try {
			activeTaskId = lock
					? temuOrderRequestTaskMapper.lockActiveExecution(task.getId(), task.getExecutionId())
					: temuOrderRequestTaskMapper.findActiveExecution(task.getId(), task.getExecutionId());
		}
		catch (RuntimeException e) {
			throw new TaskLeaseLostException("无法确认任务租约，停止执行: taskId=" + task.getId(), e);
		}
		if (activeTaskId == null) {
			throw new TaskLeaseLostException("任务执行身份或租约已失效: taskId=" + task.getId());
		}
	}

	private void validateExecutionId(String executionId) {
		if (!StringUtils.hasText(executionId)) {
			throw new IllegalArgumentException("executionId不能为空");
		}
	}

	@Override
	public boolean timeoutTask(Long taskId, Date heartbeatDeadline, String errorMessage) {
		return temuOrderRequestTaskMapper.timeoutTask(taskId, heartbeatDeadline,
				normalizeErrorMessage(errorMessage)) == 1;
	}

	private String normalizeErrorMessage(String errorMessage) {
		if (errorMessage == null || errorMessage.length() <= 2000) {
			return errorMessage;
		}
		return errorMessage.substring(0, 2000);
	}

}
