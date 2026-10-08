package com.badfisher.temu.business.order.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * Temu 父订单表 — Temu API bg.order.list.v2.get 返回的 parentOrderMap 映射
 *
 * @author lenkor
 */
@Data
@ApiModel(value = "Temu父订单", description = "父订单主数据，店铺唯一键为channelId、site、parentOrderSn")
@TableName("tb_temu_parent_order")
public class TemuParentOrder implements Serializable {

	private static final long serialVersionUID = 1L;

	@TableId(type = IdType.AUTO)
	@ApiModelProperty("数据库主键")
	private Long id;

	/** 父单号（Temu 平台唯一标识） */
	@ApiModelProperty("父订单号")
	private String parentOrderSn;

	/** 父单状态：0-全部 1-PENDING 2-UN_SHIPPING 3-CANCELED 4-SHIPPED 5-RECEIPTED */
	@ApiModelProperty("父订单状态")
	private String parentOrderStatus;

	/** 父单创建时间 */
	@ApiModelProperty("父订单创建时间，epoch秒")
	private Long parentOrderTime;

	/** 父单发货时间 */
	@ApiModelProperty("父订单发货时间，epoch秒")
	private Long parentShippingTime;

	/** 期望最晚发货时间 */
	@ApiModelProperty("最晚应发货时间，epoch秒")
	private Long expectShipLatestTime;

	/** 最晚送达时间(epoch秒) */
	@ApiModelProperty("最晚送达时间，epoch秒")
	private Long latestDeliveryTime;

	/** 父单确认时间(epoch秒) */
	@ApiModelProperty("父订单确认时间，epoch秒")
	private Long parentConfirmTime;

	/** 父单待完成时间(epoch秒) */
	@ApiModelProperty("父订单待完成时间，epoch秒")
	private Long parentOrderPendingFinishTime;

	/** API返回的父单更新时间(epoch秒)，区别于审计字段 updateTime */
	@ApiModelProperty("Temu父订单更新时间，epoch秒")
	private Long updateTimeApi;

	/** 发货方式 */
	@ApiModelProperty("发货方式")
	private Integer shippingMethod;

	/** 订单支付类型(PPD等) */
	@ApiModelProperty("订单支付类型")
	private String orderPaymentType;

	/** 是否主仓合并发货(0=否, 1=是) */
	@ApiModelProperty("是否主仓合并发货：0=否，1=是")
	private Integer isShipmentConsolidatedByMainMall;

	/** 是否含运费(0=否, 1=是) */
	@ApiModelProperty("是否含运费：0=否，1=是")
	private Integer hasShippingFee;

	/** 区域ID（美国=211） */
	@ApiModelProperty("区域ID")
	private Integer regionId;

	/** 站点ID */
	@ApiModelProperty("Temu站点ID")
	private Integer siteId;

	/** 卖家ID/店铺编码 */
	@ApiModelProperty("卖家ID/店铺编码")
	private String sellerId;

	/** ERP中台渠道编号，唯一对应一个ERP店铺 */
	@ApiModelProperty("ERP中台渠道编号")
	private Integer channelId;

	/** Temu店铺站点(US/AU/CA等) */
	@ApiModelProperty("业务站点，如US、AU、CA")
	private String site;

	/** 买家名称（从 bg.order.shippinginfo.v2.get 补充） */
	@ApiModelProperty("买家名称")
	private String buyerName;

	@TableLogic(value = "0", delval = "1")
	@ApiModelProperty("软删除标记：0=正常，1=删除")
	private Integer delFlag;

	@TableField(fill = FieldFill.INSERT)
	@ApiModelProperty("入库时间")
	private Date createTime;

	@TableField(fill = FieldFill.INSERT)
	@ApiModelProperty("创建人ID")
	private String createUserId;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	@ApiModelProperty("最后更新时间")
	private Date updateTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	@ApiModelProperty("更新人ID")
	private String updateUserId;

}
