package com.badfisher.temu.business.aftersales.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Temu 售后单状态组枚举 — 对应 Temu API bg.aftersales.aftersales.list.get 的 aftersalesStatus 字段。
 * <p>
 * 值与含义：
 * <ul>
 * <li>1 : PENDING — 待处理</li>
 * <li>2 : REQUESTED — 已申请</li>
 * <li>3 : PACKAGE_SHIPPED — 包裹已发货</li>
 * <li>4 : PLATFORM_REVIEWING — 平台审核中</li>
 * <li>5 : REFUNDED — 已退款</li>
 * <li>6 : REJECTED — 已拒绝</li>
 * <li>7 : CANCELLED — 已取消</li>
 * </ul>
 *
 * @author lenkor
 */
@Getter
public enum TemuAftersalesStatusGroup {

	PENDING(1, "Pending", "待处理"), REQUESTED(2, "Requested", "已申请"), PACKAGE_SHIPPED(3, "Package Shipped", "包裹已发货"),
	PLATFORM_REVIEWING(4, "Platform Reviewing", "平台审核中"), REFUNDED(5, "Refunded", "已退款"),
	REJECTED(6, "Rejected", "已拒绝"), CANCELLED(7, "Cancelled", "已取消");

	/** 数字状态码（API 传递用） */
	private final int code;

	/** Temu 平台原始英文名 */
	private final String engName;

	/** 中文描述 */
	private final String desc;

	private static final Map<Integer, TemuAftersalesStatusGroup> CODE_MAP = new HashMap<>();

	private static final Map<String, TemuAftersalesStatusGroup> NAME_MAP = new HashMap<>();

	static {
		for (TemuAftersalesStatusGroup status : values()) {
			CODE_MAP.put(status.code, status);
			NAME_MAP.put(status.engName, status);
		}
	}

	TemuAftersalesStatusGroup(int code, String engName, String desc) {
		this.code = code;
		this.engName = engName;
		this.desc = desc;
	}

	/** 按数字 code 查询 */
	public static TemuAftersalesStatusGroup fromCode(int code) {
		return CODE_MAP.get(code);
	}

	/** 按平台英文名查询（兼容 API 返回的字符串状态） */
	public static TemuAftersalesStatusGroup fromName(String engName) {
		return engName == null ? null : NAME_MAP.get(engName);
	}

}
