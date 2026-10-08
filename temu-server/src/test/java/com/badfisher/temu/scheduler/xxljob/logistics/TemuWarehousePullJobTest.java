package com.badfisher.temu.scheduler.xxljob.logistics;

import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.business.warehouse.service.TemuWarehousePullService;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static com.badfisher.temu.scheduler.xxljob.XxlJobTestSupport.executeJob;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemuWarehousePullJobTest {

	@Mock
	private TemuAccessTokenService temuAccessTokenService;

	@Mock
	private TemuWarehousePullService temuWarehousePullService;

	private TemuWarehousePullJob job;

	@BeforeEach
	void setUp() {
		job = new TemuWarehousePullJob();
		ReflectionTestUtils.setField(job, "temuAccessTokenService", temuAccessTokenService);
		ReflectionTestUtils.setField(job, "temuWarehousePullService", temuWarehousePullService);
	}

	@Test
	void globalShouldCallServiceOncePerAuthorizationIdentity() {
		TemuAccessToken au = account(30L, 1581, "AU");
		TemuAccessToken ca = account(31L, 1610, "CA");
		when(temuAccessTokenService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Arrays.asList(au, ca));
		when(temuWarehousePullService.doPullTask(same(au), eq(TemuAuthRegion.GLOBAL))).thenReturn(1);

		executeJob(null, job::executeGlobal);

		verify(temuWarehousePullService).doPullTask(same(au), eq(TemuAuthRegion.GLOBAL));
	}

	private static TemuAccessToken account(Long id, Integer channelId, String site) {
		TemuAccessToken account = new TemuAccessToken();
		account.setId(id);
		account.setAppId("DEMO-APP-A");
		account.setSellerId("SELLER-1");
		account.setAuthRegion(TemuAuthRegion.GLOBAL.getCode());
		account.setType(1);
		account.setChannelId(channelId);
		account.setSite(site);
		account.setAppKey("APP-KEY");
		account.setAppSecret("APP-SECRET");
		account.setAccessToken("ACCESS-TOKEN");
		account.setDelFlag(0);
		return account;
	}

}
