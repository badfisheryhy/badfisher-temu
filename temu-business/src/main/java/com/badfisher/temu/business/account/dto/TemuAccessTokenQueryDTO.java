package com.badfisher.temu.business.account.dto;

import com.badfisher.temu.business.order.dto.PageQueryDTO;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Temu Token分页查询参数。
 *
 * @author lenkor
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("Temu Token分页查询参数")
public class TemuAccessTokenQueryDTO extends PageQueryDTO {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "Temu App ID", example = "HK-TOPDON")
	private String appId;

	@ApiModelProperty(value = "中台渠道编号", example = "1001")
	private Integer channelId;

	@ApiModelProperty(value = "卖家ID", example = "634418216456832")
	private String sellerId;

	@ApiModelProperty(value = "店铺名称，支持模糊查询", example = "TOPDON")
	private String shopName;

	@ApiModelProperty(value = "站点", example = "US")
	private String site;

	@ApiModelProperty(value = "授权/API路由区域", example = "GLOBAL")
	private String authRegion;

	@ApiModelProperty(value = "授权类型(0=商品, 1=订单)", example = "1")
	private Integer type;

}
