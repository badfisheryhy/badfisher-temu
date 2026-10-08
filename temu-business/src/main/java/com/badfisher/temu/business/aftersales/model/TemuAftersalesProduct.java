package com.badfisher.temu.business.aftersales.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * Temu 售后产品表 — 子售后单关联的货品明细（productList）
 * <p>
 * 每个子售后单(aftersalesSn)可能包含多个货品，通过 aftersalesSn + productSkuId 唯一标识。
 *
 * @author lenkor
 */
@Data
@ApiModel(value = "Temu售后商品", description = "子售后单关联的商品SKU明细")
@TableName("tb_temu_aftersales_product")
public class TemuAftersalesProduct implements Serializable {

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

	/** 货品SKU ID */
	@ApiModelProperty("Temu商品SKU ID")
	private Long productSkuId;

	/** 货品编码 */
	@ApiModelProperty("货品外部编码")
	private String extCode;

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
