package com.badfisher.temu.business.aftersales.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * Temu 售后退货物流。
 * <p>
 * 数据来源：{@code bg.aftersales.parentreturnorder.get} 返回的
 * {@code result.logisticsInfoList}。一个父售后单可能包含多段退货物流， 使用卖家、父售后单号和运单号联合唯一标识。
 *
 * @author lenkor
 */
@Data
@ApiModel(value = "Temu售后退货物流", description = "父售后单下的退货承运商、运单和退货仓信息")
@TableName("tb_temu_aftersales_return_logistics")
public class TemuAftersalesReturnLogistics implements Serializable {

	private static final long serialVersionUID = 1L;

	@TableId(type = IdType.AUTO)
	@ApiModelProperty("数据库主键")
	private Long id;

	/** 父售后单号。 */
	@ApiModelProperty("父售后单号")
	private String parentAftersalesSn;

	/** 卖家ID/店铺编码。 */
	@ApiModelProperty("卖家ID/店铺编码")
	private String sellerId;

	/** ERP中台渠道编号，唯一对应一个ERP店铺。 */
	@ApiModelProperty("ERP中台渠道编号")
	private Integer channelId;

	/** Temu店铺站点(US/AU/CA等)。 */
	@ApiModelProperty("业务站点")
	private String site;

	/** 物流承运商名称。 */
	@ApiModelProperty("物流承运商名称")
	private String carrierName;

	/** 退货运单号。 */
	@ApiModelProperty("退货运单号")
	private String trackingNumber;

	/** Temu退货仓类型。 */
	@ApiModelProperty("Temu退货仓类型")
	private Integer returnWarehouseType;

	/** 退货仓一级国家/地区名称。 */
	@ApiModelProperty("退货仓一级国家或地区名称")
	private String returnWarehouseRegion1Name;

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
