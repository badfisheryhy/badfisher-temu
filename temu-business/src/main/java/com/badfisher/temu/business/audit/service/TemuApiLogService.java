package com.badfisher.temu.business.audit.service;

import com.badfisher.temu.business.audit.mapper.TemuApiLogMapper;
import com.badfisher.temu.business.audit.model.TemuApiLog;
import lombok.extern.slf4j.Slf4j;
import com.badfisher.temu.client.spi.ApiCallLogger;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 持久化 Temu API 调用日志。
 * <p>
 * 日志在 API 调用返回前同步写入，确保定时任务结束、应用关闭或调用方立即查询时记录已经落库。 日志写入失败只记录错误，不改变原 API 的业务结果。
 */
@Slf4j
@Component
public class TemuApiLogService implements ApiCallLogger {

	@Resource
	private TemuApiLogMapper temuApiLogMapper;

	@Override
	public void save(String appId, String apiName, String apiUrl, String httpMethod, Integer statusCode,
			String errorCode, String errorMessage, String requestBody, String responseBody, long costMs, int success,
			String sellerId) {
		try {
			TemuApiLog entity = new TemuApiLog();
			entity.setAppId(appId);
			entity.setApiName(apiName);
			entity.setApiUrl(apiUrl);
			entity.setHttpMethod(httpMethod);
			entity.setStatusCode(statusCode);
			entity.setErrorCode(errorCode);
			entity.setErrorMessage(errorMessage);
			entity.setRequestBody(requestBody);
			entity.setResponseBody(responseBody);
			entity.setCostTimeMs(costMs);
			entity.setSuccess(success);
			entity.setSellerId(sellerId);
			int rows = temuApiLogMapper.insert(entity);
			if (rows != 1) {
				log.error("保存 Temu API 日志失败: mapper返回行数={}, apiName={}, sellerId={}", rows, apiName, sellerId);
			}
		}
		catch (Exception e) {
			log.error("保存 Temu API 日志失败: apiName={}, sellerId={}, error={}", apiName, sellerId, e.getMessage(), e);
		}
	}

}
