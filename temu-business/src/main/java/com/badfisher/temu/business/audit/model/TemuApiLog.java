package com.badfisher.temu.business.audit.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * API 调用日志表 — 记录每次调用Temu开放平台的请求与响应
 *
 * @author lenkor
 */
@Data
@ApiModel(value = "Temu API调用日志", description = "Temu开放平台请求、响应、错误和耗时记录")
@TableName("tb_temu_api_log")
public class TemuApiLog implements Serializable {

	private static final long serialVersionUID = 1L;

	@TableId(type = IdType.AUTO)
	@ApiModelProperty("日志主键ID")
	private Long id;

	/** Temu App ID */
	@ApiModelProperty("Temu应用主体ID")
	private String appId;

	/** API名称 */
	@ApiModelProperty("API名称")
	private String apiName;

	/** 请求URL */
	@ApiModelProperty("请求URL")
	private String apiUrl;

	/** 请求方法(GET/POST) */
	@ApiModelProperty("HTTP请求方法")
	private String httpMethod;

	/** HTTP状态码 */
	@ApiModelProperty("HTTP状态码")
	private Integer statusCode;

	/** 平台错误码 */
	@ApiModelProperty("Temu平台错误码")
	private String errorCode;

	/** 错误信息 */
	@ApiModelProperty("错误信息")
	private String errorMessage;

	/** 请求体JSON */
	@ApiModelProperty("请求体JSON；列表接口不返回")
	private String requestBody;

	/** 响应体JSON */
	@ApiModelProperty("响应体JSON；列表接口不返回")
	private String responseBody;

	/** 耗时(ms) */
	@ApiModelProperty("调用耗时，单位毫秒")
	private Long costTimeMs;

	/** 卖家ID */
	@ApiModelProperty("卖家ID")
	private String sellerId;

	/** 是否成功(1=成功, 0=失败) */
	@ApiModelProperty("是否成功：1=成功，0=失败")
	private Integer success;

	@TableField(fill = FieldFill.INSERT)
	@ApiModelProperty("调用时间")
	private Date createTime;

}
