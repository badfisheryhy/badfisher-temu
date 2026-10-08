package com.badfisher.temu.business.sync.service.impl;

import com.badfisher.temu.business.sync.mapper.SyncTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 任务领取、心跳、完成及超时回收状态转换单元测试。 */
@ExtendWith(MockitoExtension.class)
class SyncTaskServiceImplTest {

	@Mock
	private SyncTaskMapper mapper;

	private SyncTaskServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new SyncTaskServiceImpl();
		ReflectionTestUtils.setField(service, "temuOrderRequestTaskMapper", mapper);
	}

	@Test
	void shouldReturnTrueOnlyWhenAtomicClaimUpdatesOneRow() {
		when(mapper.claimTask(10L, 1001, "SELLER-1", "US", "AFTERSALES", "execution-a", 600)).thenReturn(1);
		when(mapper.claimTask(11L, 1001, "SELLER-1", "US", "AFTERSALES", "execution-a", 600)).thenReturn(0);

		assertTrue(service.claimTask(10L, 1001, "SELLER-1", "US", "AFTERSALES", "execution-a"));
		assertFalse(service.claimTask(11L, 1001, "SELLER-1", "US", "AFTERSALES", "execution-a"));
	}

	@Test
	void shouldReflectHeartbeatAndTerminalCasResults() {
		when(mapper.heartbeatTask(20L, "execution-a", 600)).thenReturn(1);
		when(mapper.completeTask(20L, "execution-a")).thenReturn(1);
		when(mapper.failTask(21L, "execution-a", "failed")).thenReturn(0);

		assertTrue(service.heartbeatTask(20L, "execution-a"));
		assertTrue(service.completeTask(20L, "execution-a"));
		assertFalse(service.failTask(21L, "execution-a", "failed"));
	}

	@Test
	void shouldAtomicallyRetryFailedTaskWithNewExecutionIdentity() {
		when(mapper.retryFailedTask(40L, "execution-b", 600)).thenReturn(1);
		when(mapper.retryFailedTask(41L, "execution-b", 600)).thenReturn(0);

		assertTrue(service.retryFailedTask(40L, "execution-b"));
		assertFalse(service.retryFailedTask(41L, "execution-b"));
	}

	@Test
	void shouldPassHeartbeatDeadlineAndLimitTimeoutErrorLength() {
		Date deadline = new Date();
		String longMessage = repeat('x', 2500);
		when(mapper.timeoutTask(org.mockito.ArgumentMatchers.eq(30L), org.mockito.ArgumentMatchers.eq(deadline),
				org.mockito.ArgumentMatchers.anyString()))
			.thenReturn(1);

		assertTrue(service.timeoutTask(30L, deadline, longMessage));

		ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
		verify(mapper).timeoutTask(org.mockito.ArgumentMatchers.eq(30L), org.mockito.ArgumentMatchers.eq(deadline),
				messageCaptor.capture());
		assertEquals(2000, messageCaptor.getValue().length());
	}

	private static String repeat(char value, int count) {
		StringBuilder builder = new StringBuilder(count);
		for (int i = 0; i < count; i++) {
			builder.append(value);
		}
		return builder.toString();
	}

}
