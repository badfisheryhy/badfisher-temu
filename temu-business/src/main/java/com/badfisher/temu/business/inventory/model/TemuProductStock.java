package com.badfisher.temu.business.inventory.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * Temu 商品库存当前快照表 — 保存 bg.btg.goods.stock.quantity.get 返回的SKU仓库库存。
 * <p>
 * 每个授权主体拉取成功后在单个事务中整体替换；表中始终只保留当前可读版本，
 * 同一appId和sellerId的全部记录具有相同的snapshotVersion和snapshotTime。
 *
 * @author lenkor
 */
@Data
@ApiModel(value = "Temu商品当前库存", description = "最近一次完整同步成功后的SKU仓库库存快照")
@TableName("tb_temu_product_stock_snapshot")
public class TemuProductStock implements Serializable {

	private static final long serialVersionUID = 1L;

	@TableId(type = IdType.AUTO)
	@ApiModelProperty("数据库主键")
	private Long id;

	/** Temu应用主体ID */
	@ApiModelProperty("Temu应用主体ID")
	private String appId;

	/** 卖家ID/店铺编码 */
	@ApiModelProperty("卖家ID/店铺编码")
	private String sellerId;

	/** 店铺名称，冗余自授权表，方便查询和快照排查 */
	@ApiModelProperty("店铺名称")
	private String shopName;

	/** Temu SKC ID，本次库存请求参数 */
	@ApiModelProperty("Temu SKC ID")
	private Long productSkcId;

	/** Temu商品SKU ID */
	@ApiModelProperty("Temu商品SKU ID")
	private Long productSkuId;

	/** 仓库ID */
	@ApiModelProperty("仓库ID")
	private String warehouseId;

	/** 仓库名称 */
	@ApiModelProperty("仓库名称")
	private String warehouseName;

	/** SKU可用库存数量 */
	@ApiModelProperty("SKU可用库存数量")
	private Integer stockQuantity;

	/** 临时锁定数量 */
	@ApiModelProperty("临时锁定数量")
	private Integer tempLockQuantity;

	/** 是否启用预售(0=否, 1=是) */
	@ApiModelProperty("是否启用预售：0=否，1=是")
	private Integer enablePreSale;

	/** 发货模式 */
	@ApiModelProperty("发货模式")
	private Integer shippingMode;

	/** 当前库存快照版本号 */
	@ApiModelProperty("当前库存快照版本号")
	private String snapshotVersion;

	/** 当前库存快照生成时间 */
	@ApiModelProperty("当前库存快照生成时间")
	private Date snapshotTime;

	@TableField(fill = FieldFill.INSERT)
	@ApiModelProperty("入库时间")
	private Date createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	@ApiModelProperty("最后更新时间")
	private Date updateTime;

}
