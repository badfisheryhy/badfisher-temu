package com.badfisher.temu.scheduler.xxljob.compensation;

import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.sync.service.SyncTaskService;
import com.badfisher.temu.scheduler.xxljob.XxlJobTestSupport;
import com.xxl.job.core.context.XxlJobContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemuFailedTaskRetryOwnershipTest {

	@Mock
	private SyncTaskService taskService;

	@Mock
	private TemuAccessTokenService accountService;

	@Mock
	private ThreadPoolTaskScheduler scheduler;

	private TemuFailedTaskRetryJob job;

	@BeforeEach
	void setUp() {
		job = new TemuFailedTaskRetryJob();
		ReflectionTestUtils.setField(job, "temuOrderRequestTaskService", taskService);
		ReflectionTestUtils.setField(job, "temuAccessTokenService", accountService);
		ReflectionTestUtils.setField(job, "heartbeatScheduler", scheduler);
	}

	@Test
	void retryingRunningTaskMustNotFailItsCurrentExecution() {
		when(taskService.getById(1L)).thenReturn(task(1));

		XxlJobContext result = XxlJobTestSupport.executeJob("1", job::retryFailedTask);

		assertEquals(XxlJobContext.HANDLE_CODE_FAIL, result.getHandleCode());
		verify(taskService, never()).retryFailedTask(eq(1L), anyString());
		verify(taskService, never()).failTask(any(), any(), any());
	}

	@Test
	void losingClaimRaceMustNotFailWinner() {
		when(taskService.getById(1L)).thenReturn(task(3));
		when(accountService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
			.thenReturn(Collections.singletonList(account()));
		when(taskService.retryFailedTask(eq(1L), anyString())).thenReturn(false);

		XxlJobContext result = XxlJobTestSupport.executeJob("1", job::retryFailedTask);

		assertEquals(XxlJobContext.HANDLE_CODE_FAIL, result.getHandleCode());
		verify(taskService, never()).failTask(any(), any(), any());
		verify(scheduler, never()).scheduleAtFixedRate(any(Runnable.class), eq(60_000L));
	}

	@Test
	void heartbeatStartupFailureCanOnlyFailTheNewlyClaimedExecution() {
		when(taskService.getById(1L)).thenReturn(task(3));
		when(accountService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
			.thenReturn(Collections.singletonList(account()));
		when(taskService.retryFailedTask(eq(1L), anyString())).thenReturn(true);
		when(scheduler.scheduleAtFixedRate(any(Runnable.class), eq(60_000L)))
			.thenThrow(new IllegalStateException("scheduler unavailable"));

		XxlJobContext result = XxlJobTestSupport.executeJob("1", job::retryFailedTask);

		assertEquals(XxlJobContext.HANDLE_CODE_FAIL, result.getHandleCode());
		ArgumentCaptor<String> executionId = ArgumentCaptor.forClass(String.class);
		verify(taskService).retryFailedTask(eq(1L), executionId.capture());
		assertFalse("old-execution".equals(executionId.getValue()));
		verify(taskService).failTask(1L, executionId.getValue(), "scheduler unavailable");
	}

	private static SyncTask task(int state) {
		SyncTask task = new SyncTask();
		task.setId(1L);
		task.setAppId("demo-app");
		task.setAccountCode("demo-seller");
		task.setAuthRegion("US");
		task.setSite("US");
		task.setChannelId(1001);
		task.setPullType("ORDER");
		task.setState(state);
		task.setExecutionId("old-execution");
		return task;
	}

	private static TemuAccessToken account() {
		TemuAccessToken account = new TemuAccessToken();
		account.setId(1L);
		account.setAppId("demo-app");
		account.setSellerId("demo-seller");
		account.setAuthRegion("US");
		account.setSite("US");
		account.setChannelId(1001);
		account.setType(1);
		account.setAppKey("demo-key");
		account.setAppSecret("demo-secret");
		account.setAccessToken("demo-token");
		return account;
	}

}
