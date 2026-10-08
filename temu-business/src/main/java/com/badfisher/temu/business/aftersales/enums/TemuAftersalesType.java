package com.badfisher.temu.business.aftersales.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Temu 售后类型枚举 — 对应 Temu API 的 afterSalesType 字段。
 * <p>
 * 值与含义：
 * <ul>
 * <li>1 : REFUND_ONLY — 仅退款</li>
 * <li>2 : RETURN_AND_REFUND — 退货退款</li>
 * </ul>
 *
 * @author lenkor
 */
@Getter
public enum TemuAftersalesType {

	REFUND_ONLY(1, "refund only", "仅退款"), RETURN_AND_REFUND(2, "return and refund", "退货退款");

	/** 数字状态码（API 传递用） */
	private final int code;

	/** Temu 平台原始英文名 */
	private final String engName;

	/** 中文描述 */
	private final String desc;

	private static final Map<Integer, TemuAftersalesType> CODE_MAP = new HashMap<>();

	private static final Map<String, TemuAftersalesType> NAME_MAP = new HashMap<>();

	static {
		for (TemuAftersalesType type : values()) {
			CODE_MAP.put(type.code, type);
			NAME_MAP.put(type.engName, type);
		}
	}

	TemuAftersalesType(int code, String engName, String desc) {
		this.code = code;
		this.engName = engName;
		this.desc = desc;
	}

	/** 按数字 code 查询 */
	public static TemuAftersalesType fromCode(int code) {
		return CODE_MAP.get(code);
	}

	/** 按平台英文名查询（兼容 API 返回的字符串状态） */
	public static TemuAftersalesType fromName(String engName) {
		return engName == null ? null : NAME_MAP.get(engName);
	}

}
