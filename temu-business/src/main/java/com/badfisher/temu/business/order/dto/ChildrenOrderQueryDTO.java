package com.badfisher.temu.business.order.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 子订单查询参数 — 按父单号查询子订单列表
 *
 * @author lenkor
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("子订单查询参数")
public class ChildrenOrderQueryDTO extends PageQueryDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "父单号（必填）", required = true)
	private String parentOrderSn;

	@ApiModelProperty(value = "卖家ID/店铺编码")
	private String sellerId;

	@ApiModelProperty(value = "中台渠道编号（ERP店铺唯一标识）")
	private Integer channelId;

	@ApiModelProperty(value = "站点（US/AU/CA等）")
	private String site;

	@ApiModelProperty(value = "子订单状态(3=已取消)")
	private Integer orderStatus;

}
