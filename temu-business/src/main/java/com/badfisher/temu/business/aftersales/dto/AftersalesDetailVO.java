package com.badfisher.temu.business.aftersales.dto;

import com.badfisher.temu.business.aftersales.model.TemuAftersalesProduct;
import com.badfisher.temu.business.aftersales.model.TemuAftersalesReturnLogistics;
import com.badfisher.temu.business.aftersales.model.TemuChildrenAftersales;
import com.badfisher.temu.business.aftersales.model.TemuParentAftersales;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 父售后单及其子售后、商品和退货物流聚合视图。 */
@Data
@ApiModel(value = "Temu售后聚合详情", description = "一次返回父售后单及其关联的子售后、商品和退货物流")
public class AftersalesDetailVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty("父售后单")
	private TemuParentAftersales parentAftersales;

	@ApiModelProperty("父售后单下的子售后单")
	private List<TemuChildrenAftersales> childrenAftersales;

	@ApiModelProperty("父售后单下的售后商品")
	private List<TemuAftersalesProduct> products;

	@ApiModelProperty("父售后单下的退货物流")
	private List<TemuAftersalesReturnLogistics> returnLogistics;

}
