package com.badfisher.temu.business.product.dto;

import com.badfisher.temu.business.order.dto.PageQueryDTO;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * Temu 商品与 SKU 分页查询条件。
 *
 * @author lenkor
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "Temu商品查询参数", description = "用于商品档案和SKU档案的分页筛选")
public class ProductQueryDTO extends PageQueryDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty("Temu应用主体ID")
	private String appId;

	@ApiModelProperty("卖家ID/店铺编码")
	private String sellerId;

	@ApiModelProperty("店铺名称，支持模糊查询")
	private String shopName;

	@ApiModelProperty("Temu商品ID")
	private Long productId;

	@ApiModelProperty("Temu SKC ID")
	private Long productSkcId;

	@ApiModelProperty("Temu商品SKU ID")
	private Long productSkuId;

	@ApiModelProperty("商品或SKU外部编码，支持模糊查询")
	private String extCode;

	@ApiModelProperty("商品名称，支持模糊查询")
	private String productName;

	@ApiModelProperty("SKC站点状态")
	private Integer skcSiteStatus;

}
