package com.badfisher.temu.business.product.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * Temu 商品 SKU 表 — 保存商品列表中 productSkuSummaries 数组。
 * <p>
 * 唯一键为 (app_id, seller_id, product_sku_id)，通过 product_id 关联商品主表。
 *
 * @author lenkor
 */
@Data
@ApiModel(value = "Temu商品SKU", description = "商品规格SKU档案，租户唯一键为appId、sellerId、productSkuId")
@TableName("tb_temu_product_sku")
public class TemuProductSku implements Serializable {

	private static final long serialVersionUID = 1L;

	@TableId(type = IdType.AUTO)
	@ApiModelProperty("数据库主键")
	private Long id;

	/** Temu 应用主体ID */
	@ApiModelProperty("Temu应用主体ID")
	private String appId;

	/** 卖家ID/店铺编码 */
	@ApiModelProperty("卖家ID/店铺编码")
	private String sellerId;

	/** 店铺名称，冗余自授权表，方便查询 */
	@ApiModelProperty("店铺名称")
	private String shopName;

	/** 所属商品ID */
	@ApiModelProperty("所属Temu商品ID")
	private Long productId;

	/** 所属SKC ID */
	@ApiModelProperty("所属Temu SKC ID")
	private Long productSkcId;

	/** Temu 商品SKU ID */
	@ApiModelProperty("Temu商品SKU ID")
	private Long productSkuId;

	/** SKU外部编码 */
	@ApiModelProperty("SKU外部编码")
	private String extCode;

	/** 虚拟库存 */
	@ApiModelProperty("虚拟库存")
	private Integer virtualStock;

	/** SKU发货模式 */
	@ApiModelProperty("SKU发货模式")
	private Integer shippingMode;

	/** SKU重量，保持Temu接口原始计量值 milligrams */
	@ApiModelProperty("SKU重量，Temu原始计量值")
	private Long weight;

	/** 包裹长度，保持Temu接口原始计量值 mm */
	@ApiModelProperty("包裹长度，Temu原始计量值")
	private Integer packageLength;

	/** 包裹宽度，保持Temu接口原始计量值 mm */
	@ApiModelProperty("包裹宽度，Temu原始计量值")
	private Integer packageWidth;

	/** 包裹高度，保持Temu接口原始计量值 mm */
	@ApiModelProperty("包裹高度，Temu原始计量值")
	private Integer packageHeight;

	/** 是否敏感品(0=否, 1=是) */
	@ApiModelProperty("是否敏感品：0=否，1=是")
	private Integer sensitiveFlag;

	/** 低频扩展信息(JSON对象)，包含规格、条码及敏感品明细 */
	@ApiModelProperty("规格、条码和敏感品扩展信息JSON")
	private String extensionJson;

	/** 最近一次成功同步该记录的任务ID */
	@ApiModelProperty("最近一次成功同步任务ID")
	private Long syncTaskId;

	@TableLogic(value = "0", delval = "1")
	@ApiModelProperty("软删除标记：0=正常，1=删除")
	private Integer delFlag;

	@TableField(fill = FieldFill.INSERT)
	@ApiModelProperty("入库时间")
	private Date createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	@ApiModelProperty("最后更新时间")
	private Date updateTime;

}
