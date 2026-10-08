package com.badfisher.temu.business.warehouse.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * Temu 仓库表 — 通过 bg.logistics.warehouse.list.get 获取的发货仓库列表
 * <p>
 * 唯一索引：(seller_id, warehouse_id)，仓库不归属ERP渠道。
 *
 * @author lenkor
 */
@Data
@ApiModel(value = "Temu仓库", description = "卖家维度的发货仓库档案，唯一键为sellerId、warehouseId")
@TableName("tb_temu_warehouse")
public class TemuWarehouse implements Serializable {

	private static final long serialVersionUID = 1L;

	@TableId(type = IdType.AUTO)
	@ApiModelProperty("数据库主键")
	private Long id;

	/** 卖家ID/店铺编码 */
	@ApiModelProperty("卖家ID/店铺编码")
	private String sellerId;

	/** 仓库ID */
	@ApiModelProperty("仓库ID")
	private String warehouseId;

	/** 仓库名称 */
	@ApiModelProperty("仓库名称")
	private String warehouseName;

	/** 区域ID（美国=211） */
	@ApiModelProperty("仓库一级区域ID")
	private Integer regionId1;

	/** 仓库管理类型 */
	@ApiModelProperty("仓库管理类型")
	private Integer warehouseManagementType;

	/** 是否默认仓库(0=否, 1=是) */
	@ApiModelProperty("是否默认仓库：0=否，1=是")
	private Integer defaultWarehouse;

	/** 是否推送订单到开放平台(0=否, 1=是) */
	@ApiModelProperty("是否向开放平台推送订单：0=否，1=是")
	private Integer pushOrderToOpenPlatform;

	/** 是否启用购买面单(0=否, 1=是) */
	@ApiModelProperty("是否启用购买面单：0=否，1=是")
	private Integer enableBuyShippingLabel;

	@TableLogic(value = "0", delval = "1")
	@ApiModelProperty("软删除标记：0=正常，1=删除")
	private Integer delFlag;

	@TableField(fill = FieldFill.INSERT)
	@ApiModelProperty("创建时间")
	private Date createTime;

	@TableField(fill = FieldFill.INSERT)
	@ApiModelProperty("创建人ID")
	private String createUserId;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	@ApiModelProperty("更新时间")
	private Date updateTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	@ApiModelProperty("更新人ID")
	private String updateUserId;

}
