package com.badfisher.temu.business.inventory.dto;

import com.badfisher.temu.business.order.dto.PageQueryDTO;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * Temu 仓库和当前库存分页查询条件。
 *
 * @author lenkor
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "Temu库存查询参数", description = "用于仓库档案和SKU仓库库存快照筛选")
public class InventoryQueryDTO extends PageQueryDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty("Temu应用主体ID；查询库存时使用")
	private String appId;

	@ApiModelProperty("卖家ID/店铺编码")
	private String sellerId;

	@ApiModelProperty("店铺名称，支持模糊查询")
	private String shopName;

	@ApiModelProperty("Temu SKC ID")
	private Long productSkcId;

	@ApiModelProperty("Temu商品SKU ID")
	private Long productSkuId;

	@ApiModelProperty("仓库ID")
	private String warehouseId;

	@ApiModelProperty("仓库名称，支持模糊查询")
	private String warehouseName;

	@ApiModelProperty("最小可用库存")
	private Integer minStockQuantity;

	@ApiModelProperty("最大可用库存")
	private Integer maxStockQuantity;

	@ApiModelProperty("是否默认仓库：0=否，1=是")
	private Integer defaultWarehouse;

	@ApiModelProperty("快照时间起，格式：yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private Date snapshotTimeStart;

	@ApiModelProperty("快照时间止，格式：yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private Date snapshotTimeEnd;

}
