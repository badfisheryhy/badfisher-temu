package com.badfisher.temu.business.account.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.ToString;
import com.badfisher.temu.client.auth.TemuCredentials;

import java.io.Serializable;
import java.util.Date;

/**
 * Temu 账号配置表 — 店铺授权信息、站点
 *
 * @author lenkor
 */
@Data
@TableName("tb_temu_access_token")
public class TemuAccessToken implements Serializable, TemuCredentials {

	private static final long serialVersionUID = 1L;

	@TableId(type = IdType.AUTO)
	private Long id;

	/** Temu App ID */
	private String appId;

	/** 中台渠道编号 */
	private Integer channelId;

	/** Temu App Key（用于 API 签名） */
	@ToString.Exclude
	private String appKey;

	/** Temu App Secret（用于 MD5 签名） */
	@ToString.Exclude
	private String appSecret;

	/** 卖家ID */
	private String sellerId;

	/** 店铺名称 */
	private String shopName;

	/** 站点区域(US/AU/CN) */
	private String site;

	/** Temu授权/API路由区域(US/GLOBAL/EU/CN)，与业务站点site相互独立。 */
	private String authRegion;

	/** 类型(0=商品, 1=订单) */
	private Integer type;

	/** 访问令牌 */
	@ToString.Exclude
	private String accessToken;

	/** Token有效天数；为空表示未配置固定有效期 */
	private Integer expireDays;

	/** Token过期日期；创建时按创建时间计算，更新Token时按本次更新时间重新计算 */
	private Date expireTime;

	@TableLogic(value = "0", delval = "1")
	private Integer delFlag;

	@TableField(fill = FieldFill.INSERT)
	private Date createTime;

	@TableField(fill = FieldFill.INSERT)
	private String createUserId;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private Date updateTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateUserId;

	/**
	 * 判断 Token 是否已到期，提前一分钟视为过期，避免临界时间发起无效请求。 expireTime 为空时表示未配置固定有效期，不在本地拦截。
	 */
	public boolean isExpired() {
		return expireTime != null && System.currentTimeMillis() >= expireTime.getTime() - 60_000L;
	}

}
