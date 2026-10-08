package com.badfisher.temu.business.aftersales.dto;

import com.badfisher.temu.business.order.dto.PageQueryDTO;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 售后单查询参数
 *
 * @author lenkor
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("售后单查询参数")
public class AftersalesQueryDTO extends PageQueryDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "父售后单号（模糊查询）")
	private String parentAftersalesSn;

	@ApiModelProperty(value = "子售后单号（模糊查询）")
	private String aftersalesSn;

	@ApiModelProperty(value = "卖家ID/店铺编码")
	private String sellerId;

	@ApiModelProperty(value = "中台渠道编号（ERP店铺唯一标识）")
	private Integer channelId;

	@ApiModelProperty(value = "站点（US/AU/CA等）")
	private String site;

	@ApiModelProperty(value = "父订单号（模糊查询）")
	private String parentOrderSn;

	@ApiModelProperty(value = "Temu售后状态原始码；不同售后层级的状态含义不同")
	private Integer aftersalesStatus;

	@ApiModelProperty(value = "售后类型(1=仅退款, 2=退货退款)")
	private Integer aftersalesType;

	@ApiModelProperty(value = "API创建时间-开始")
	private Date createTimeStart;

	@ApiModelProperty(value = "API创建时间-结束")
	private Date createTimeEnd;

}
