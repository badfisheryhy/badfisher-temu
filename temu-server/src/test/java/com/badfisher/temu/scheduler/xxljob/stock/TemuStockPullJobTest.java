package com.badfisher.temu.scheduler.xxljob.stock;

import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.business.inventory.service.TemuStockPullService;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static com.badfisher.temu.scheduler.xxljob.XxlJobTestSupport.executeJob;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemuStockPullJobTest {

	@Mock
	private TemuAccessTokenService temuAccessTokenService;

	@Mock
	private TemuStockPullService temuStockPullService;

	private TemuStockPullJob job;

	private ThreadPoolTaskExecutor scheduleExecutor;

	@BeforeEach
	void setUp() {
		scheduleExecutor = new ThreadPoolTaskExecutor();
		scheduleExecutor.setCorePoolSize(1);
		scheduleExecutor.setMaxPoolSize(1);
		scheduleExecutor.setQueueCapacity(10);
		scheduleExecutor.initialize();

		job = new TemuStockPullJob();
		ReflectionTestUtils.setField(job, "temuAccessTokenService", temuAccessTokenService);
		ReflectionTestUtils.setField(job, "temuStockPullService", temuStockPullService);
		ReflectionTestUtils.setField(job, "temuScheduleExecutor", scheduleExecutor);
	}

	@AfterEach
	void tearDown() {
		scheduleExecutor.shutdown();
	}

	@Test
	void shouldDeduplicateAuthorizationIdentityAndIgnoreChannel() {
		TemuAccessToken first = account(1L, null);
		TemuAccessToken duplicate = account(2L, 1610);
		when(temuAccessTokenService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Arrays.asList(first, duplicate));
		when(temuStockPullService.pullAccountStock(first, TemuAuthRegion.CN)).thenReturn(3);

		executeJob(null, job::executeCn);

		verify(temuStockPullService, times(1)).pullAccountStock(first, TemuAuthRegion.CN);
	}

	private static TemuAccessToken account(Long id, Integer channelId) {
		TemuAccessToken account = new TemuAccessToken();
		account.setId(id);
		account.setAppId("DEMO-APP-A");
		account.setSellerId("SELLER-1");
		account.setAuthRegion("CN");
		account.setType(0);
		account.setChannelId(channelId);
		account.setAppKey("APP-KEY");
		account.setAppSecret("APP-SECRET");
		account.setAccessToken("ACCESS-TOKEN");
		account.setDelFlag(0);
		return account;
	}

}
