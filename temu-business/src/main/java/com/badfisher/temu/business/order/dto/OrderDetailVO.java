package com.badfisher.temu.business.order.dto;

import com.badfisher.temu.business.order.model.TemuChildrenOrder;
import com.badfisher.temu.business.order.model.TemuOrderAddress;
import com.badfisher.temu.business.order.model.TemuOrderProduct;
import com.badfisher.temu.business.order.model.TemuParentOrder;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 父订单及其子单、货品和收货地址聚合视图。 */
@Data
@ApiModel(value = "Temu订单聚合详情", description = "一次返回父订单及其关联的子订单、货品和收货地址")
public class OrderDetailVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty("父订单")
	private TemuParentOrder parentOrder;

	@ApiModelProperty("父订单下的子订单")
	private List<TemuChildrenOrder> childrenOrders;

	@ApiModelProperty("父订单下的订单货品")
	private List<TemuOrderProduct> products;

	@ApiModelProperty("订单收货地址；未拉取到时为空")
	private TemuOrderAddress address;

}
