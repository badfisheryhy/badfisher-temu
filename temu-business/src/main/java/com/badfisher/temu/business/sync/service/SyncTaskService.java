package com.badfisher.temu.business.sync.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.badfisher.temu.business.sync.model.SyncTask;

import java.util.Date;
import java.util.List;

/**
 * Temu 订单请求任务 服务接口
 *
 * @author lenkor
 */
public interface SyncTaskService extends IService<SyncTask> {

	SyncTask findLastTaskByAuth(String appId, String accountCode, String authRegion, String pullType);

	List<SyncTask> getUnRequestTaskByAuth(String appId, String accountCode, String authRegion,
			String pullType);

	boolean claimTaskByAuth(Long taskId, String appId, String accountCode, String authRegion, String pullType,
			String executionId);

	/** 根据店铺编码、站点和拉取类型查找最近一次任务 */
	SyncTask findLastTaskByAccountCode(Integer channelId, String accountCode, String site, String pullType);

	/** 获取店铺在指定站点、指定拉取类型下的所有未执行任务(state=0)。 */
	List<SyncTask> getUnRequestTask(Integer channelId, String accountCode, String site, String pullType);

	/** 按任务、店铺、站点和拉取类型执行 CAS 原子领取，返回是否领取成功。 */
	boolean claimTask(Long taskId, Integer channelId, String accountCode, String site, String pullType,
			String executionId);

	/** 原子重新领取失败任务，成功时清空旧错误并将state从3改为1。 */
	boolean retryFailedTask(Long taskId, String executionId);

	/** 仅为执行身份匹配且租约未过期的任务续租。 */
	boolean heartbeatTask(Long taskId, String executionId);

	/** 将仍处于执行中的任务原子标记为完成。 */
	boolean completeTask(Long taskId, String executionId);

	/** 将仍处于执行中的任务原子标记为失败。 */
	boolean failTask(Long taskId, String executionId, String errorMessage);

	/** 平台调用及分页前检查执行身份和租约；无法确认有效时中止执行。 */
	void checkExecution(SyncTask task);

	/** 在业务写入的同一事务内锁定并校验任务行，锁持有到该事务结束。 */
	void lockExecution(SyncTask task);

	/** 原子回收超过截止时间仍未续租的执行中任务。 */
	boolean timeoutTask(Long taskId, Date heartbeatDeadline, String errorMessage);

}
