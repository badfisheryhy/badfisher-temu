package com.badfisher.temu.business.audit.dto;

import com.badfisher.temu.business.order.dto.PageQueryDTO;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * Temu API 调用日志分页及统计查询条件。
 *
 * @author lenkor
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "Temu API日志查询参数", description = "支持按接口、店铺、成功状态、耗时和调用时间筛选")
public class ApiLogQueryDTO extends PageQueryDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty("Temu应用主体ID")
	private String appId;

	@ApiModelProperty("API名称，支持模糊查询")
	private String apiName;

	@ApiModelProperty("卖家ID")
	private String sellerId;

	@ApiModelProperty("是否成功：1=成功，0=失败")
	private Integer success;

	@ApiModelProperty("HTTP状态码")
	private Integer statusCode;

	@ApiModelProperty("平台错误码")
	private String errorCode;

	@ApiModelProperty("最小耗时，单位毫秒")
	private Long minCostTimeMs;

	@ApiModelProperty("最大耗时，单位毫秒")
	private Long maxCostTimeMs;

	@ApiModelProperty("调用时间起，格式：yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private Date createTimeStart;

	@ApiModelProperty("调用时间止，格式：yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private Date createTimeEnd;

}
