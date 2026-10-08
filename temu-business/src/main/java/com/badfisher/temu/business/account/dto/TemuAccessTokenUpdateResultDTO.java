package com.badfisher.temu.business.account.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Temu Token 更新结果。响应中不返回 accessToken、appKey 和 appSecret 等敏感字段。
 *
 * @author lenkor
 */
@Data
@ApiModel("Temu Token更新结果")
public class TemuAccessTokenUpdateResultDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty("授权记录主键ID")
	private Long id;

	@ApiModelProperty("Temu App ID")
	private String appId;

	@ApiModelProperty("中台渠道编号")
	private Integer channelId;

	@ApiModelProperty("卖家ID")
	private String sellerId;

	@ApiModelProperty("店铺名称")
	private String shopName;

	@ApiModelProperty("站点")
	private String site;

	@ApiModelProperty("授权/API路由区域")
	private String authRegion;

	@ApiModelProperty("授权类型(0=商品, 1=订单)")
	private Integer type;

	@ApiModelProperty("Token有效天数")
	private Integer expireDays;

	@ApiModelProperty("Token过期日期")
	private Date expireTime;

	@ApiModelProperty("授权记录创建时间")
	private Date createTime;

	@ApiModelProperty("更新时间")
	private Date updateTime;

	@ApiModelProperty("Token是否已过期")
	private Boolean expired;

}
