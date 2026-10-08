package com.badfisher.temu.business.order.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Temu 父订单金额表 — Temu API bg.order.amount.query 返回的 parentOrderMap 映射
 *
 * @author lenkor
 */
@Data
@TableName("tb_temu_parent_order_amount")
public class TemuParentOrderAmount implements Serializable {

	private static final long serialVersionUID = 1L;

	@TableId(type = IdType.AUTO)
	private Long id;

	/** 父单号 */
	private String parentOrderSn;

	/** 卖家ID/店铺编码 */
	private String sellerId;

	/** 货币 (USD/AU/...) */
	private String currency;

	/** 客户实付 */
	private Long customerPaid;

	/** 税费总额 */
	private Long taxTotal;

	/** 退款总额 */
	private Long refundsTotal;

	/** 预估收入 */
	private Long estimatedRevenue;

	/** 预估收入扣减 */
	private Long estimatedRevenueDeduction;

	/** 平台折扣 */
	private Long discountFromTemu;

	/** 卖家折扣 */
	private Long discountFromSeller;

	/** 总折扣 */
	private Long totalDiscount;

	/** 运费总额 */
	private Long shippingAmountTotal;

	/** 基础价总额 */
	private Long basePriceTotal;

	/** 零售价总额 */
	private Long retailPriceTotal;

	@TableLogic(value = "0", delval = "1")
	private Integer delFlag;

	@TableField(fill = FieldFill.INSERT)
	private Date createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private Date updateTime;

}
