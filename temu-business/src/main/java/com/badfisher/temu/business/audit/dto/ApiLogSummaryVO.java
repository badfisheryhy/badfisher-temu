package com.badfisher.temu.business.audit.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** API 调用日志按接口名称聚合后的耗时和成功率统计。 */
@Data
@ApiModel(value = "Temu API调用统计", description = "按API名称分组的调用次数、耗时和失败数")
public class ApiLogSummaryVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty("API名称")
	private String apiName;

	@ApiModelProperty("调用次数")
	private Long executeCount;

	@ApiModelProperty("平均耗时，单位毫秒")
	private BigDecimal avgCostTimeMs;

	@ApiModelProperty("最长耗时，单位毫秒")
	private Long maxCostTimeMs;

	@ApiModelProperty("最短耗时，单位毫秒")
	private Long minCostTimeMs;

	@ApiModelProperty("失败次数")
	private Long failedCount;

}
