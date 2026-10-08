package com.badfisher.temu.business.order.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * Temu 订单地址表 — 通过 bg.order.shippinginfo.v2.get 获取的收货地址
 * <p>
 * 地址接口在发货后可能不可用，会使用备份地址兜底。
 *
 * @author lenkor
 */
@Data
@ApiModel(value = "Temu订单地址", description = "父订单的收货地址；敏感字段仅供授权业务使用")
@TableName("tb_temu_order_address")
public class TemuOrderAddress implements Serializable {

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

	/** 收货人姓名 */
	@ApiModelProperty("收货人姓名")
	private String receiptName;

	/** 收货人手机号（虚拟号），格式：11位手机号-4位分机号 */
	@ApiModelProperty("收货人手机号或虚拟号")
	private String mobile;

	/** 区域地址1（州/省） */
	@ApiModelProperty("一级区域")
	private String regionname1;

	/** 区域地址2（城市） */
	@ApiModelProperty("二级区域")
	private String regionname2;

	/** 区域地址3（区/县） */
	@ApiModelProperty("三级区域")
	private String regionname3;

	/** 详细地址行1 */
	@ApiModelProperty("详细地址行1")
	private String addressline1;

	/** 详细地址行2 */
	@ApiModelProperty("详细地址行2")
	private String addressline2;

	/** 邮编 */
	@ApiModelProperty("邮编")
	private String postCode;

	/** Temu 匿名邮箱 */
	@ApiModelProperty("Temu匿名邮箱")
	private String mail;

	/** 国家标识（如 US） */
	@ApiModelProperty("国家代码")
	private String countryCode;

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
