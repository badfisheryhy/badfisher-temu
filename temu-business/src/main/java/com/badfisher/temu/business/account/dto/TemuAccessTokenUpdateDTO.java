package com.badfisher.temu.business.account.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * Temu Token 更新参数。
 *
 * @author lenkor
 */
@Data
@ApiModel("Temu Token更新参数")
public class TemuAccessTokenUpdateDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "授权记录主键ID", required = true, example = "2")
	private Long id;

	@ApiModelProperty(value = "新的access_token", required = true,
			example = "REPLACE_WITH_ACCESS_TOKEN")
	@lombok.ToString.Exclude
	private String accessToken;

	@ApiModelProperty(value = "Token有效天数，范围1~3650", required = true, example = "320")
	private Integer expireDays;

	@ApiModelProperty(value = "中台渠道编号；不传则保留原值", example = "1001")
	private Integer channelId;

	@ApiModelProperty(value = "授权/API路由区域；不传则保留原值", example = "GLOBAL")
	private String authRegion;

	@ApiModelProperty(value = "更新人ID", example = "1")
	private String updateUserId;

}
