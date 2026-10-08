package com.badfisher.temu.scheduler.xxljob.compensation;

import com.badfisher.temu.scheduler.xxljob.compensation.TemuFailedTaskRetryJob;

import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.sync.service.SyncTaskService;
import com.xxl.job.core.context.XxlJobContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.badfisher.temu.scheduler.xxljob.XxlJobTestSupport.executeJob;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 失败任务补偿线上集成测试。
 * <p>
 * 直接调用 {@link TemuFailedTaskRetryJob#retryFailedTask(String)}，会访问真实 Temu API，
 * 并将任务及业务数据真实写入数据库，不回滚。
 * <p>
 * 前置条件：
 * <ol>
 * <li>{@code TEMU_LIVE_TESTS=true}</li>
 * <li>{@code TEMU_FAILED_TASK_IDS=任务ID}，多个任务使用英文逗号分隔</li>
 * <li>指定任务存在、未删除且当前为失败状态（state=3）</li>
 * </ol>
 */
@Slf4j
@SpringBootTest(classes = com.badfisher.temu.server.TemuApplication.class)
@EnabledIfEnvironmentVariable(named = "TEMU_LIVE_TESTS", matches = "true")
class TemuFailedTaskRetryJobIntegrationTest {

	@Resource
	private TemuFailedTaskRetryJob temuFailedTaskRetryJob;

	@Resource
	private SyncTaskService temuOrderRequestTaskService;

	@Test
	@DisplayName("retryFailedTask - 重跑失败任务并验证真实落库")
	void retryFailedTaskShouldExecuteAndPersist() {
		String taskIdParam = "165";
		if (taskIdParam == null || taskIdParam.trim().isEmpty()) {
			throw new IllegalStateException("请设置 TEMU_FAILED_TASK_IDS，值为一个或多个 state=3 的任务ID");
		}

		List<Long> taskIds = TemuFailedTaskRetryJob.parseTaskIds(taskIdParam);
		Map<Long, SyncTask> tasksBeforeRetry = new LinkedHashMap<>();
		for (Long taskId : taskIds) {
			SyncTask task = temuOrderRequestTaskService.getById(taskId);
			assertNotNull(task, "失败任务不存在: taskId=" + taskId);
			assertEquals(Integer.valueOf(0), task.getDelFlag(), "失败任务已删除: taskId=" + taskId);
			assertEquals(Integer.valueOf(3), task.getState(),
					"仅允许测试 state=3 的失败任务: taskId=" + taskId + ", state=" + task.getState());
			tasksBeforeRetry.put(taskId, task);
			log.info(
					"重跑前: taskId={}, appId={}, sellerId={}, authRegion={}, site={}, pullType={}, "
							+ "window=[{} - {}], error={}",
					task.getId(), task.getAppId(), task.getAccountCode(), task.getAuthRegion(), task.getSite(),
					task.getPullType(), task.getBeginTime(), task.getEndTime(), task.getErrorMessage());
		}

		XxlJobContext jobResult = executeJob(taskIdParam, temuFailedTaskRetryJob::retryFailedTask);
		assertEquals(XxlJobContext.HANDLE_CODE_SUCCESS, jobResult.getHandleCode(), jobResult.getHandleMsg());

		for (Map.Entry<Long, SyncTask> entry : tasksBeforeRetry.entrySet()) {
			Long taskId = entry.getKey();
			SyncTask before = entry.getValue();
			SyncTask after = temuOrderRequestTaskService.getById(taskId);
			assertNotNull(after, "重跑后任务不存在: taskId=" + taskId);
			assertEquals(Integer.valueOf(2), after.getState(), "任务重跑后应落库为 state=2: taskId=" + taskId + ", state="
					+ after.getState() + ", error=" + after.getErrorMessage());
			assertNull(after.getErrorMessage(), "任务重跑成功后 error_message 应清空: taskId=" + taskId);
			assertEquals(before.getBeginTime(), after.getBeginTime(), "重跑不应改变原任务 begin_time: taskId=" + taskId);
			assertEquals(before.getEndTime(), after.getEndTime(), "重跑不应改变原任务 end_time: taskId=" + taskId);
			log.info("重跑成功并已落库: taskId={}, state={}, window=[{} - {}]", taskId, after.getState(), after.getBeginTime(),
					after.getEndTime());
		}
	}

}
