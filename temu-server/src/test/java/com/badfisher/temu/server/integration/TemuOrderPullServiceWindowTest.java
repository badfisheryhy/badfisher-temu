package com.badfisher.temu.server.integration;

import com.badfisher.temu.business.order.service.TemuOrderPullService;
import com.badfisher.temu.business.sync.service.SyncTaskService;

import com.badfisher.temu.client.TemuApiClient;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.badfisher.temu.client.exception.TemuApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemuOrderPullServiceWindowTest {

	@Mock
	private TemuApiClient temuApiClient;

	@Mock
	private SyncTaskService temuOrderRequestTaskService;

	@InjectMocks
	private TemuOrderPullService service;

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void shouldSplitOversizedInitialWindowBeforePersistingFirstPage() {
		TemuAccessToken account = account();
		SyncTask task = task();

		when(temuApiClient.callByAuthRegion(eq(TemuAuthRegion.US), anyMap(), eq(account)))
			.thenReturn(oversizedResponse(), emptyResponse(), emptyResponse());
		when(temuOrderRequestTaskService.completeTask(1L, "execution-a")).thenReturn(true);

		service.doPullTask(account, task, TemuAuthRegion.US);

		ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
		verify(temuApiClient, org.mockito.Mockito.times(3)).callByAuthRegion(eq(TemuAuthRegion.US),
				paramsCaptor.capture(), eq(account));
		List<Map<String, Object>> params = paramsCaptor.getAllValues();
		assertWindow(params.get(0), 100L, 200L);
		assertWindow(params.get(1), 100L, 150L);
		assertWindow(params.get(2), 151L, 200L);
		verify(temuOrderRequestTaskService).completeTask(1L, "execution-a");
		verify(temuOrderRequestTaskService, never()).failTask(eq(1L), eq("execution-a"), anyString());
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void shouldSplitWindowWhenTemuReturns21008Directly() {
		TemuAccessToken account = account();
		SyncTask task = task();
		TemuApiException resultLimit = new TemuApiException(null, "21008", "Search result count exceeds maximum limit",
				"https://example.test", "{}", "{}");
		when(temuApiClient.callByAuthRegion(eq(TemuAuthRegion.US), anyMap(), eq(account))).thenThrow(resultLimit)
			.thenReturn(emptyResponse(), emptyResponse());
		when(temuOrderRequestTaskService.completeTask(1L, "execution-a")).thenReturn(true);

		service.doPullTask(account, task, TemuAuthRegion.US);

		ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
		verify(temuApiClient, org.mockito.Mockito.times(3)).callByAuthRegion(eq(TemuAuthRegion.US),
				paramsCaptor.capture(), eq(account));
		List<Map<String, Object>> params = paramsCaptor.getAllValues();
		assertWindow(params.get(1), 100L, 150L);
		assertWindow(params.get(2), 151L, 200L);
		verify(temuOrderRequestTaskService).completeTask(1L, "execution-a");
		verify(temuOrderRequestTaskService, never()).failTask(eq(1L), eq("execution-a"), anyString());
	}

	private static TemuAccessToken account() {
		TemuAccessToken account = new TemuAccessToken();
		account.setSellerId("SELLER-1");
		return account;
	}

	private static SyncTask task() {
		SyncTask task = new SyncTask();
		task.setId(1L);
		task.setExecutionId("execution-a");
		task.setAccountCode("SELLER-1");
		task.setBeginTime(new Date(100_000L));
		task.setEndTime(new Date(200_000L));
		task.setPullStatus("0");
		return task;
	}

	private static Map<String, Object> oversizedResponse() {
		Map<String, Object> result = new HashMap<>();
		result.put("pageItems", Collections.singletonList(Collections.emptyMap()));
		result.put("totalItemNum", 10_001);
		Map<String, Object> response = new HashMap<>();
		response.put("result", result);
		return response;
	}

	private static Map<String, Object> emptyResponse() {
		Map<String, Object> result = new HashMap<>();
		result.put("pageItems", Collections.emptyList());
		result.put("totalItemNum", 0);
		Map<String, Object> response = new HashMap<>();
		response.put("result", result);
		return response;
	}

	private static void assertWindow(Map<String, Object> params, long start, long end) {
		assertEquals(start, ((Number) params.get("updateAtStart")).longValue());
		assertEquals(end, ((Number) params.get("updateAtEnd")).longValue());
	}

}
