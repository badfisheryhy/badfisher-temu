package com.badfisher.temu.server.api.audit;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.badfisher.temu.business.audit.mapper.TemuApiLogMapper;
import com.badfisher.temu.business.audit.dto.ApiLogQueryDTO;
import com.badfisher.temu.business.audit.model.TemuApiLog;
import com.badfisher.temu.business.audit.dto.ApiLogSummaryVO;
import com.badfisher.temu.server.web.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Temu 开放平台 API 调用日志查询接口。
 * <p>
 * 列表默认不返回体积较大的请求体和响应体，完整报文通过详情接口查询。
 * </p>
 *
 * @author lenkor
 */
@Api(tags = "Temu API日志查询")
@RestController
@RequestMapping("/temu/api-log")
@RequiredArgsConstructor
public class TemuApiLogController {

	private final TemuApiLogMapper apiLogMapper;

	@ApiOperation(value = "分页查询API调用日志", notes = "按调用时间倒序；列表不返回requestBody和responseBody")
	@GetMapping("/list")
	public Result<IPage<TemuApiLog>> listLogs(ApiLogQueryDTO query) {
		Page<TemuApiLog> page = new Page<>(query.normalizedPageNum(), query.normalizedPageSize());
		IPage<TemuApiLog> result = apiLogMapper.selectPage(page,
				Wrappers.lambdaQuery(TemuApiLog.class)
					.select(TemuApiLog::getId, TemuApiLog::getAppId, TemuApiLog::getApiName, TemuApiLog::getApiUrl,
							TemuApiLog::getHttpMethod, TemuApiLog::getStatusCode, TemuApiLog::getErrorCode,
							TemuApiLog::getErrorMessage, TemuApiLog::getCostTimeMs, TemuApiLog::getSellerId,
							TemuApiLog::getSuccess, TemuApiLog::getCreateTime)
					.eq(query.getAppId() != null, TemuApiLog::getAppId, query.getAppId())
					.like(query.getApiName() != null, TemuApiLog::getApiName, query.getApiName())
					.eq(query.getSellerId() != null, TemuApiLog::getSellerId, query.getSellerId())
					.eq(query.getSuccess() != null, TemuApiLog::getSuccess, query.getSuccess())
					.eq(query.getStatusCode() != null, TemuApiLog::getStatusCode, query.getStatusCode())
					.eq(query.getErrorCode() != null, TemuApiLog::getErrorCode, query.getErrorCode())
					.ge(query.getMinCostTimeMs() != null, TemuApiLog::getCostTimeMs, query.getMinCostTimeMs())
					.le(query.getMaxCostTimeMs() != null, TemuApiLog::getCostTimeMs, query.getMaxCostTimeMs())
					.ge(query.getCreateTimeStart() != null, TemuApiLog::getCreateTime, query.getCreateTimeStart())
					.le(query.getCreateTimeEnd() != null, TemuApiLog::getCreateTime, query.getCreateTimeEnd())
					.orderByDesc(TemuApiLog::getCreateTime));
		return Result.success(result);
	}

	@ApiOperation(value = "查询API调用日志详情", notes = "返回请求体、响应体、错误信息和耗时等完整信息，报文可能较大")
	@GetMapping("/{id}")
	public Result<TemuApiLog> getLog(@ApiParam(value = "API日志主键ID", required = true) @PathVariable Long id) {
		TemuApiLog log = apiLogMapper.selectById(id);
		return log == null ? Result.fail("API调用日志不存在: " + id) : Result.success(log);
	}

	@ApiOperation(value = "按API名称统计调用情况", notes = "统计调用次数、平均/最长/最短耗时和失败次数，按平均耗时倒序")
	@GetMapping("/summary")
	public Result<List<ApiLogSummaryVO>> summarize(ApiLogQueryDTO query) {
		return Result.success(apiLogMapper.selectSummary(query));
	}

}
