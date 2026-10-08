package com.badfisher.temu.business.order.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Temu 子订单金额表 — Temu API bg.order.amount.query 返回的 orderList 每条子单金额
 *
 * @author lenkor
 */
@Data
@TableName("tb_temu_children_order_amount")
public class TemuChildrenOrderAmount implements Serializable {

	private static final long serialVersionUID = 1L;

	@TableId(type = IdType.AUTO)
	private Long id;

	/** 父单号 */
	private String parentOrderSn;

	/** 子单号 */
	private String orderSn;

	/** 卖家ID/店铺编码 */
	private String sellerId;

	/** 货币 (USD/AU/...) */
	private String currency;

	/** 数量 */
	private Integer quantity;

	/** 平台折扣 */
	private Long discountFromTemu;

	/** 卖家折扣 */
	private Long discountFromSeller;

	/** 运费 */
	private Long shipAmountTotal;

	/** 单件基础价 */
	private Long unitBasePrice;

	/** 单件零售价 */
	private Long unitRetailPrice;

	/** 基础价(单价×数量) */
	private Long basePrice;

	@TableLogic(value = "0", delval = "1")
	private Integer delFlag;

	@TableField(fill = FieldFill.INSERT)
	private Date createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private Date updateTime;

}
