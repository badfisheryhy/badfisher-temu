package com.badfisher.temu.scheduler.xxljob;

import com.badfisher.temu.business.audit.mapper.TemuApiLogMapper;
import com.badfisher.temu.business.sync.service.SyncTaskService;
import com.badfisher.temu.scheduler.xxljob.amount.TemuOrderAmountRemediationJob;
import com.badfisher.temu.scheduler.xxljob.cleanup.TemuApiLogCleanupJob;
import com.badfisher.temu.scheduler.xxljob.compensation.TemuFailedTaskRetryJob;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.handler.impl.MethodJobHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 经 XXL-JOB 的真实方法适配器验证参数与结果协议，不连接调度中心或业务数据库。 */
class XxlJobAdapterTest {

	private XxlJobContext previousContext;

	@BeforeEach
	void rememberContext() {
		previousContext = XxlJobContext.getXxlJobContext();
	}

	@AfterEach
	void restoreContext() {
		XxlJobContext.setXxlJobContext(previousContext);
	}

	@Test
	void shouldReadRetryTaskIdFromSchedulerContext() throws Exception {
		SyncTaskService taskService = mock(SyncTaskService.class);
		TemuFailedTaskRetryJob job = new TemuFailedTaskRetryJob();
		ReflectionTestUtils.setField(job, "temuOrderRequestTaskService", taskService);

		XxlJobContext result = invoke(job, "retryFailedTask", "123");

		verify(taskService, atLeastOnce()).getById(123L);
		assertEquals(XxlJobContext.HANDLE_CODE_FAIL, result.getHandleCode());
		assertTrue(result.getHandleMsg().contains("任务不存在或已删除"));
	}

	@Test
	void shouldReportExplicitFailureInsteadOfIgnoringReturnValue() throws Exception {
		XxlJobContext result = invoke(new TemuOrderAmountRemediationJob(), "executeSingle", "sellerOnly:");

		assertEquals(XxlJobContext.HANDLE_CODE_FAIL, result.getHandleCode());
		assertTrue(result.getHandleMsg().contains("参数格式错误"));
	}

	@Test
	void shouldReportCleanupExceptionAsFailure() throws Exception {
		TemuApiLogMapper mapper = mock(TemuApiLogMapper.class);
		when(mapper.deleteExpiredLogs(any(Date.class), eq(5000)))
			.thenThrow(new IllegalStateException("database unavailable"));
		TemuApiLogCleanupJob job = new TemuApiLogCleanupJob();
		ReflectionTestUtils.setField(job, "temuApiLogMapper", mapper);

		XxlJobContext result = invoke(job, "execute", "");

		assertEquals(XxlJobContext.HANDLE_CODE_FAIL, result.getHandleCode());
		assertTrue(result.getHandleMsg().contains("database unavailable"));
	}

	@Test
	void shouldReportSuccessAfterNoArgumentInvocation() throws Exception {
		TemuApiLogMapper mapper = mock(TemuApiLogMapper.class);
		TemuApiLogCleanupJob job = new TemuApiLogCleanupJob();
		ReflectionTestUtils.setField(job, "temuApiLogMapper", mapper);

		XxlJobContext result = invoke(job, "execute", "");

		verify(mapper).deleteExpiredLogs(any(Date.class), eq(5000));
		assertEquals(XxlJobContext.HANDLE_CODE_SUCCESS, result.getHandleCode());
		assertEquals("Temu API日志清理任务执行成功", result.getHandleMsg());
	}

	private XxlJobContext invoke(Object job, String methodName, String parameter) throws Exception {
		XxlJobContext context = new XxlJobContext(1L, parameter, null, 0, 1);
		XxlJobContext.setXxlJobContext(context);
		MethodJobHandler handler = new MethodJobHandler(job, job.getClass().getMethod(methodName), null, null);
		handler.execute();
		return context;
	}

}
