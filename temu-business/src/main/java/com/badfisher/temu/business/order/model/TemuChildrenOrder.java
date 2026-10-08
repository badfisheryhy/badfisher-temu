package com.badfisher.temu.business.order.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * Temu 子订单表 — Temu API 返回的 orderList 中的每条子订单
 * <p>
 * 每个父订单(parentOrderSn)下包含一到多个子订单(orderSn)。
 *
 * @author lenkor
 */
@Data
@ApiModel(value = "Temu子订单", description = "父订单下的购买项和下单时商品快照")
@TableName("tb_temu_children_order")
public class TemuChildrenOrder implements Serializable {

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

	/** 子订单SN（平台唯一） */
	@ApiModelProperty("子订单号")
	private String orderSn;

	/** 商品SKU ID(goodsId) */
	@ApiModelProperty("下单商品SKU标识goodsId")
	private Long goodsId;

	/** 商品名称 */
	@ApiModelProperty("下单时商品名称")
	private String goodsName;

	/** 订单状态（3=已取消） */
	@ApiModelProperty("子订单状态")
	private Integer orderStatus;

	/** 商品数量 */
	@ApiModelProperty("购买数量")
	private Integer quantity;

	/** 货品SKU ID */
	@ApiModelProperty("货品SKU标识skuId")
	private Long skuId;

	/** 商品规格描述 */
	@ApiModelProperty("下单时商品规格")
	private String spec;

	/** 商品缩略图URL */
	@ApiModelProperty("下单时商品缩略图")
	private String thumbUrl;

	@ApiModelProperty("软删除标记")
	private Integer delFlag;

	@TableField(fill = FieldFill.INSERT)
	@ApiModelProperty("入库时间")
	private Date createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	@ApiModelProperty("最后更新时间")
	private Date updateTime;

}
