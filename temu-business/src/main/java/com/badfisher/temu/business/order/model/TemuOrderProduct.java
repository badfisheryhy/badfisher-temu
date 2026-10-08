package com.badfisher.temu.business.order.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * Temu 订单货品表 — 子订单关联的货品明细（productList）
 * <p>
 * 每个子订单(orderSn)可能包含多个货品，通过 childrenOrderNo + productSkuId 唯一标识。 注意：此为订单级别的货品明细，非商品
 * catalog。
 *
 * @author lenkor
 */
@Data
@ApiModel(value = "Temu订单货品", description = "子订单关联的货品和商品SKU明细")
@TableName("tb_temu_order_product")
public class TemuOrderProduct implements Serializable {

	private static final long serialVersionUID = 1L;

	@TableId(type = IdType.AUTO)
	@ApiModelProperty("数据库主键")
	private Long id;

	/** 父单号 */
	@ApiModelProperty("父订单号")
	private String parentOrderSn;

	/** 卖家ID/店铺编码 */
	@ApiModelProperty("卖家ID/店铺编码")
	private String sellerId;

	/** ERP中台渠道编号，唯一对应一个ERP店铺 */
	@ApiModelProperty("ERP中台渠道编号")
	private Integer channelId;

	/** Temu店铺站点(US/AU/CA等) */
	@ApiModelProperty("业务站点")
	private String site;

	/** 子订单号 */
	@ApiModelProperty("子订单号")
	private String childrenOrderNo;

	/** 货品编码 */
	@ApiModelProperty("货品外部编码")
	private String extCode;

	/** 货品ID */
	@ApiModelProperty("Temu商品ID")
	private Long productId;

	/** 货品SKU ID */
	@ApiModelProperty("Temu商品SKU ID")
	private Long productSkuId;

	/** 商品与货品数量转换系数（商品数量 quantity × soldFactor = 货品数量） */
	@ApiModelProperty("商品与货品数量转换系数")
	private String soldFactor;

	@ApiModelProperty("软删除标记")
	private Integer delFlag;

	@TableField(fill = FieldFill.INSERT)
	@ApiModelProperty("入库时间")
	private Date createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	@ApiModelProperty("最后更新时间")
	private Date updateTime;

}
