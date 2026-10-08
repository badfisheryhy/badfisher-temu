package com.badfisher.temu.business.order.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Temu 父订单状态枚举 — 对应 Temu API bg.order.list.v2.get 的 parentOrderStatus 字段。
 * <p>
 * 值与含义：
 * <ul>
 * <li>0 : All — 全部</li>
 * <li>1 : PENDING — 待处理</li>
 * <li>2 : UN_SHIPPING — 待发货</li>
 * <li>3 : CANCELED — 已取消</li>
 * <li>4 : SHIPPED — 已发货</li>
 * <li>5 : RECEIPTED — 已签收</li>
 * <li>41 : PARTIALLY_SHIPPED — 部分发货（仅本地仓）</li>
 * <li>51 : PARTIALLY_RECEIVED — 部分签收（仅本地仓）</li>
 * </ul>
 *
 * @author lenkor
 */
@Getter
public enum TemuOrderStatus {

	ALL(0, "All", "全部"), PENDING(1, "PENDING", "待处理"), UN_SHIPPING(2, "UN_SHIPPING", "待发货"),
	CANCELED(3, "CANCELED", "已取消"), SHIPPED(4, "SHIPPED", "已发货"), RECEIPTED(5, "RECEIPTED", "已签收"),
	PARTIALLY_SHIPPED(41, "PARTIALLY_SHIPPED", "部分发货"), PARTIALLY_RECEIVED(51, "PARTIALLY_RECEIVED", "部分签收");

	/** 数字状态码（API 传递用） */
	private final int code;

	/** Temu 平台原始英文名 */
	private final String engName;

	/** 中文描述 */
	private final String desc;

	private static final Map<Integer, TemuOrderStatus> CODE_MAP = new HashMap<>();

	private static final Map<String, TemuOrderStatus> NAME_MAP = new HashMap<>();

	static {
		for (TemuOrderStatus status : values()) {
			CODE_MAP.put(status.code, status);
			NAME_MAP.put(status.engName, status);
		}
	}

	TemuOrderStatus(int code, String engName, String desc) {
		this.code = code;
		this.engName = engName;
		this.desc = desc;
	}

	/** 按数字 code 查询 */
	public static TemuOrderStatus fromCode(int code) {
		return CODE_MAP.get(code);
	}

	/** 按平台英文名查询（兼容 API 返回的字符串状态） */
	public static TemuOrderStatus fromName(String engName) {
		return engName == null ? null : NAME_MAP.get(engName);
	}

}
