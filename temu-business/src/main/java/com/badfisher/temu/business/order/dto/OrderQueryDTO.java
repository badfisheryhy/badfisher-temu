package com.badfisher.temu.business.order.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 订单查询参数
 *
 * @author lenkor
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("订单查询参数")
public class OrderQueryDTO extends PageQueryDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "父单号（模糊查询）")
	private String parentOrderSn;

	@ApiModelProperty(value = "子单号（模糊查询）")
	private String orderSn;

	@ApiModelProperty(value = "卖家ID/店铺编码")
	private String sellerId;

	@ApiModelProperty(value = "中台渠道编号（ERP店铺唯一标识）")
	private Integer channelId;

	@ApiModelProperty(value = "站点（US/AU/CA等）")
	private String site;

	@ApiModelProperty(value = "父单状态(参见TemuOrderStatus枚举: 1=待处理 2=待发货 3=已取消 4=已发货 5=已签收)")
	private String parentOrderStatus;

	@ApiModelProperty(value = "买家名称（模糊查询）")
	private String buyerName;

	@ApiModelProperty(value = "父单创建时间-开始")
	private Date parentOrderTimeStart;

	@ApiModelProperty(value = "父单创建时间-结束")
	private Date parentOrderTimeEnd;

}
