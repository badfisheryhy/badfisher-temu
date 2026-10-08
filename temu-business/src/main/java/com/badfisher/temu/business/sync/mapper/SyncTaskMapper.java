package com.badfisher.temu.business.sync.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.badfisher.temu.business.sync.model.SyncTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * Temu 订单请求任务 Mapper
 *
 * @author lenkor
 */
@Mapper
public interface SyncTaskMapper extends BaseMapper<SyncTask> {

	SyncTask findLastTaskByAuth(@Param("appId") String appId, @Param("accountCode") String accountCode,
			@Param("authRegion") String authRegion, @Param("pullType") String pullType);

	List<SyncTask> getUnRequestTaskByAuth(@Param("appId") String appId,
			@Param("accountCode") String accountCode, @Param("authRegion") String authRegion,
			@Param("pullType") String pullType);

	int claimTaskByAuth(@Param("taskId") Long taskId, @Param("appId") String appId,
			@Param("accountCode") String accountCode, @Param("authRegion") String authRegion,
			@Param("pullType") String pullType, @Param("executionId") String executionId,
			@Param("leaseSeconds") int leaseSeconds);

	/** 查询指定店铺某种业务类型的最近一次任务（用于计算下一次时间窗口的起始时间） */
	SyncTask findLastTaskByAccountCode(@Param("channelId") Integer channelId,
			@Param("accountCode") String accountCode, @Param("site") String site, @Param("pullType") String pullType);

	/** 查询指定店铺、站点和业务类型的所有待处理任务(state=0)。 */
	List<SyncTask> getUnRequestTask(@Param("channelId") Integer channelId,
			@Param("accountCode") String accountCode, @Param("site") String site, @Param("pullType") String pullType);

	/**
	 * 原子领取任务：任务归属条件完全匹配且仍处于未请求状态时，才能改为请求中。
	 * @return 成功领取返回 1，否则返回 0
	 */
	int claimTask(@Param("taskId") Long taskId, @Param("channelId") Integer channelId,
			@Param("accountCode") String accountCode, @Param("site") String site, @Param("pullType") String pullType,
			@Param("executionId") String executionId, @Param("leaseSeconds") int leaseSeconds);

	/** 原子重新领取失败任务：只有state=3时才能进入执行中。 */
	int retryFailedTask(@Param("taskId") Long taskId, @Param("executionId") String executionId,
			@Param("leaseSeconds") int leaseSeconds);

	/** 刷新执行中任务的最后心跳时间。 */
	int heartbeatTask(@Param("taskId") Long taskId, @Param("executionId") String executionId,
			@Param("leaseSeconds") int leaseSeconds);

	/** 仅允许 state=1 的当前执行任务完成。 */
	int completeTask(@Param("taskId") Long taskId, @Param("executionId") String executionId);

	/** 仅允许 state=1 的当前执行任务失败。 */
	int failTask(@Param("taskId") Long taskId, @Param("executionId") String executionId,
			@Param("errorMessage") String errorMessage);

	Long findActiveExecution(@Param("taskId") Long taskId, @Param("executionId") String executionId);

	Long lockActiveExecution(@Param("taskId") Long taskId, @Param("executionId") String executionId);

	/** 仅回收心跳已过期且仍处于 state=1 的任务。 */
	int timeoutTask(@Param("taskId") Long taskId, @Param("heartbeatDeadline") Date heartbeatDeadline,
			@Param("errorMessage") String errorMessage);

}
