package com.badfisher.temu.business.aftersales.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * Temu 父售后单表 — bg.aftersales.parentaftersales.list.get 返回的父售后单记录
 * <p>
 * 父售后单是售后的主单，一个父售后单对应一个父订单下的售后申请， 通过 bg.aftersales.aftersales.list.get 查询其下属的子售后单明细。
 *
 * @author lenkor
 */
@Data
@ApiModel(value = "Temu父售后单", description = "售后主单及其关联的原父订单")
@TableName("tb_temu_parent_aftersales")
public class TemuParentAftersales implements Serializable {

	private static final long serialVersionUID = 1L;

	@TableId(type = IdType.AUTO)
	@ApiModelProperty("数据库主键")
	private Long id;

	/** 父售后单号（Temu 平台唯一标识） */
	@ApiModelProperty("父售后单号")
	private String parentAftersalesSn;

	/** 卖家ID/店铺编码 */
	@ApiModelProperty("卖家ID/店铺编码")
	private String sellerId;

	/** ERP中台渠道编号，唯一对应一个ERP店铺 */
	@ApiModelProperty("ERP中台渠道编号")
	private Integer channelId;

	/** Temu店铺站点(US/AU/CA等) */
	@ApiModelProperty("业务站点")
	private String site;

	/** 父订单号 */
	@ApiModelProperty("原父订单号")
	private String parentOrderSn;

	/** 售后状态组 */
	@ApiModelProperty("售后状态组")
	private Integer aftersalesStatusGroup;

	/** 父售后状态 */
	@ApiModelProperty("父售后状态")
	private Integer parentAftersalesStatus;

	/** 售后类型(1=仅退款, 2=退货退款)。 */
	@ApiModelProperty("售后类型：1=仅退款，2=退货退款")
	private Integer aftersalesType;

	/** 退货配送类型 */
	@ApiModelProperty("退货配送类型")
	private Integer returnDeliveryType;

	/** API返回的创建时间(epoch秒，详情接口毫秒值入库前会转换)。 */
	@ApiModelProperty("Temu售后创建时间，epoch秒")
	private Long createTimeApi;

	/** API返回的更新时间(epoch秒，详情接口毫秒值入库前会转换)。 */
	@ApiModelProperty("Temu售后更新时间，epoch秒")
	private Long updateTimeApi;

	@TableLogic(value = "0", delval = "1")
	@ApiModelProperty("软删除标记")
	private Integer delFlag;

	@TableField(fill = FieldFill.INSERT)
	@ApiModelProperty("入库时间")
	private Date createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	@ApiModelProperty("最后更新时间")
	private Date updateTime;

}
