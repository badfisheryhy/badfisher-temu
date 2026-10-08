package com.badfisher.temu.business.product.dto;

import com.badfisher.temu.business.product.model.TemuProduct;
import com.badfisher.temu.business.product.model.TemuProductSku;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 商品档案及其 SKU 聚合视图。 */
@Data
@ApiModel(value = "Temu商品聚合详情", description = "商品档案及其全部SKU")
public class ProductDetailVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty("商品档案")
	private TemuProduct product;

	@ApiModelProperty("商品下的SKU列表")
	private List<TemuProductSku> skus;

}
