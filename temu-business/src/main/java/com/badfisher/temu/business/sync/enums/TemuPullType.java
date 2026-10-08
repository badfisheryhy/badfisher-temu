package com.badfisher.temu.business.sync.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Temu 拉取数据类型枚举 — 区分 tb_temu_order_request_task 表中任务拉取的业务数据类型
 * <p>
 * 每个拉取类型绑定一个 {@link #accountType} 属性，用于标识该类型所需 {@code tb_temu_access_token.type} 的授权类型：
 * <ul>
 * <li>{@code 0} — 商品类型授权</li>
 * <li>{@code 1} — 订单类型授权</li>
 * </ul>
 * BuildJob/PullJob 在遍历启用店铺时按该属性过滤账号，避免商品账号跑订单拉取或反之。
 *
 * @author lenkor
 */
@Getter
public enum TemuPullType {

	/** 订单 — 需订单类型授权 */
	ORDER("ORDER", "订单", 1),
	/** 订单金额 — 需订单类型授权 */
	ORDER_AMOUNT("ORDER_AMOUNT", "订单金额", 1),
	/** 商品档案 — 需要商品类型授权 */
	PRODUCT("PRODUCT", "商品", 0),
	/** 售后单 — 需订单类型授权 */
	AFTERSALES("AFTERSALES", "售后单", 1),
	/** 库存 — 需商品类型授权 */
	STOCK("STOCK", "库存", 0);

	private final String code;

	private final String desc;

	/** 对应 tb_temu_access_token.type 的授权类型（0=商品, 1=订单） */
	private final int accountType;

	private static final Map<String, TemuPullType> CODE_MAP = new HashMap<>();

	static {
		for (TemuPullType type : values()) {
			CODE_MAP.put(type.code, type);
		}
	}

	TemuPullType(String code, String desc, int accountType) {
		this.code = code;
		this.desc = desc;
		this.accountType = accountType;
	}

	public static TemuPullType fromCode(String code) {
		return code == null ? null : CODE_MAP.get(code);
	}

}
