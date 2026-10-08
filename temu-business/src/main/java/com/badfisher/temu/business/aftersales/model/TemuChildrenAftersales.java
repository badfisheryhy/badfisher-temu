package com.badfisher.temu.business.aftersales.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * Temu 子售后单表 — bg.aftersales.aftersales.list.get 返回的子售后单记录
 * <p>
 * 子售后单隶属于父售后单，通过 parentAftersalesSn 关联。 每个子售后单对应一个具体的商品 SKU 的售后申请，包含商品信息和售后产品明细。
 *
 * @author lenkor
 */
@Data
@ApiModel(value = "Temu子售后单", description = "关联到具体子订单和商品SKU的售后明细")
@TableName("tb_temu_children_aftersales")
public class TemuChildrenAftersales implements Serializable {

	private static final long serialVersionUID = 1L;

	@TableId(type = IdType.AUTO)
	@ApiModelProperty("数据库主键")
	private Long id;

	/** 父售后单号 */
	@ApiModelProperty("父售后单号")
	private String parentAftersalesSn;

	/** 子售后单号 */
	@ApiModelProperty("子售后单号")
	private String aftersalesSn;

	/** 卖家ID/店铺编码 */
	@ApiModelProperty("卖家ID/店铺编码")
	private String sellerId;

	/** ERP中台渠道编号，唯一对应一个ERP店铺 */
	@ApiModelProperty("ERP中台渠道编号")
	private Integer channelId;

	/** Temu店铺站点(US/AU/CA等) */
	@ApiModelProperty("业务站点")
	private String site;

	/** 子订单号，由父售后详情接口返回。 */
	@ApiModelProperty("原子订单号")
	private String orderSn;

	/** Temu子售后状态原始码；不与父售后状态组共用枚举。 */
	@ApiModelProperty("子售后状态原始码")
	private Integer aftersalesStatus;

	/** 售后类型(1=仅退款, 2=退货退款)。 */
	@ApiModelProperty("售后类型：1=仅退款，2=退货退款")
	private Integer aftersalesType;

	/** 售后原因编码。 */
	@ApiModelProperty("售后原因编码")
	private Integer aftersalesReasonCode;

	/** 售后原因描述。 */
	@ApiModelProperty("售后原因描述")
	private String aftersalesReasonDesc;

	/** 买家填写的售后说明。 */
	@ApiModelProperty("买家售后说明")
	private String buyerComment;

	/** 商品ID */
	@ApiModelProperty("商品标识goodsId")
	private Long goodsId;

	/** 货品SKU ID */
	@ApiModelProperty("货品SKU标识skuId")
	private Long skuId;

	/** 商品SKU ID */
	@ApiModelProperty("Temu商品SKU ID")
	private Long productSkuId;

	/** 申请售后商品数量 */
	@ApiModelProperty("申请售后商品数量")
	private Integer applyGoodsNumber;

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
