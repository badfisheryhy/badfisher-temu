package com.badfisher.temu.business.product.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * Temu 商品档案表 — 保存 bg.glo.goods.list.get 返回的商品维度数据。
 * <p>
 * 商品不归属ERP渠道，数据库唯一键为 (app_id, seller_id, product_id)。复杂且可能扩展的 属性以 JSON
 * 字符串保存，常用检索字段单独结构化，避免接口新增字段导致实体频繁变更。
 *
 * @author lenkor
 */
@Data
@ApiModel(value = "Temu商品档案", description = "商品/SPU-SKC维度档案，租户唯一键为appId、sellerId、productId")
@TableName("tb_temu_product")
public class TemuProduct implements Serializable {

	private static final long serialVersionUID = 1L;

	@TableId(type = IdType.AUTO)
	@ApiModelProperty("数据库主键")
	private Long id;

	/** Temu应用主体ID，用于接口路由、签名和问题排查，不作为店铺唯一标识 */
	@ApiModelProperty("Temu应用主体ID")
	private String appId;

	/** 卖家ID/店铺编码 */
	@ApiModelProperty("卖家ID/店铺编码")
	private String sellerId;

	/** 店铺名称，冗余自授权表，方便按店铺名称查询商品 */
	@ApiModelProperty("店铺名称")
	private String shopName;

	/** Temu 商品ID */
	@ApiModelProperty("Temu商品ID")
	private Long productId;

	/** Temu SKC ID */
	@ApiModelProperty("Temu SKC ID")
	private Long productSkcId;

	/** 商品名称 */
	@ApiModelProperty("商品名称")
	private String productName;

	/** 商品维度外部编码 */
	@ApiModelProperty("商品外部编码")
	private String extCode;

	/** 商品主图地址 */
	@ApiModelProperty("商品主图地址")
	private String mainImageUrl;

	/** 末级类目ID */
	@ApiModelProperty("末级类目ID")
	private Long leafCatId;

	/** 末级类目名称 */
	@ApiModelProperty("末级类目名称")
	private String leafCatName;

	/** SKC站点状态 */
	@ApiModelProperty("SKC站点状态")
	private Integer skcSiteStatus;

	/** 是否支持个性化(0=否, 1=是) */
	@ApiModelProperty("是否支持个性化：0=否，1=是")
	private Integer supportPersonalization;

	/** 是否匹配SKC JIT模式(0=否, 1=是) */
	@ApiModelProperty("是否匹配SKC JIT模式：0=否，1=是")
	private Integer matchSkcJitMode;

	/** Temu 返回的商品创建时间(epoch毫秒) */
	@ApiModelProperty("Temu商品创建时间，epoch毫秒")
	private Long createdAtApi;

	/** 商品属性(JSON数组) */
	@ApiModelProperty("商品属性JSON数组")
	private String propertiesJson;

	/** 低频扩展信息(JSON对象)，包含绑定站点、类目层级、JIT及半托管信息 */
	@ApiModelProperty("绑定站点、类目、JIT等扩展信息JSON")
	private String extensionJson;

	/** 最近一次成功同步该记录的任务ID，用于整轮完成后的快照对账 */
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
