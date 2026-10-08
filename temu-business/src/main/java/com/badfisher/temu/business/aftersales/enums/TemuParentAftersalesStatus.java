package com.badfisher.temu.business.aftersales.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Temu 父售后单状态枚举 — 对应 Temu API bg.aftersales.parentaftersales.list.get 的
 * parentAfterSalesStatus 字段。
 * <p>
 * 值与含义：
 * <ul>
 * <li>1 : BUYER_APPLIED_REFUND_PENDING — 买家已申请退款，待处理</li>
 * <li>2 : BUYER_SHIPPED_RETURN — 买家已寄回退货包裹</li>
 * <li>3 : RETURN_RECEIVED_PENDING_MERCHANT — 退货包裹已签收，待商家处理</li>
 * <li>4 : REFUND_INITIATED_PROCESSING — 退款已发起，系统处理中</li>
 * <li>5 : REFUND_ISSUED — 退款已到账</li>
 * <li>6 : BUYER_CANCELLED — 买家已取消售后申请</li>
 * <li>7 : AFTERSALES_REJECTED — 售后申请已拒绝</li>
 * <li>8 : BUYER_USED_LABEL_PENDING_REVIEW — 买家已使用商家退货标签，待商家审核上传发货标签</li>
 * <li>9 : REFUND_INITIATED_SYSTEM_PROCESSING — 退款已发起，系统处理中</li>
 * <li>10 : BUYER_APPLIED_RETURN — 买家已申请退货</li>
 * <li>11 : PLATFORM_REVIEWING — 平台审核中</li>
 * </ul>
 *
 * @author lenkor
 */
@Getter
public enum TemuParentAftersalesStatus {

	BUYER_APPLIED_REFUND_PENDING(1, "Buyer has applied for a refund, pending processing", "买家已申请退款，待处理"),
	BUYER_SHIPPED_RETURN(2, "Buyer has shipped the return package", "买家已寄回退货包裹"),
	RETURN_RECEIVED_PENDING_MERCHANT(3, "Return package has been received, pending merchant processing",
			"退货包裹已签收，待商家处理"),
	REFUND_INITIATED_PROCESSING(4, "Refund has been initiated, being processed by the system", "退款已发起，系统处理中"),
	REFUND_ISSUED(5, "Refund has been issued", "退款已到账"),
	BUYER_CANCELLED(6, "Buyer has cancelled the after-sales request", "买家已取消售后申请"),
	AFTERSALES_REJECTED(7, "After-sales request has been rejected", "售后申请已拒绝"),
	BUYER_USED_LABEL_PENDING_REVIEW(8,
			"Buyer has used a shipping label, provided by the merchant for return, pending merchant review and upload of the shipping label",
			"买家已使用商家退货标签，待商家审核上传发货标签"),
	REFUND_INITIATED_SYSTEM_PROCESSING(9, "Refund initiated, being processed by the system", "退款已发起，系统处理中"),
	BUYER_APPLIED_RETURN(10, "Buyer has applied for a return", "买家已申请退货"),
	PLATFORM_REVIEWING(11, "Under platform review", "平台审核中");

	/** 数字状态码（API 传递用） */
	private final int code;

	/** Temu 平台原始英文名 */
	private final String engName;

	/** 中文描述 */
	private final String desc;

	private static final Map<Integer, TemuParentAftersalesStatus> CODE_MAP = new HashMap<>();

	private static final Map<String, TemuParentAftersalesStatus> NAME_MAP = new HashMap<>();

	static {
		for (TemuParentAftersalesStatus status : values()) {
			CODE_MAP.put(status.code, status);
			NAME_MAP.put(status.engName, status);
		}
	}

	TemuParentAftersalesStatus(int code, String engName, String desc) {
		this.code = code;
		this.engName = engName;
		this.desc = desc;
	}

	/** 按数字 code 查询 */
	public static TemuParentAftersalesStatus fromCode(int code) {
		return CODE_MAP.get(code);
	}

	/** 按平台英文名查询（兼容 API 返回的字符串状态） */
	public static TemuParentAftersalesStatus fromName(String engName) {
		return engName == null ? null : NAME_MAP.get(engName);
	}

}
