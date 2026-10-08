package com.badfisher.temu.client.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Temu 开放平台接口 type 枚举 — 按业务模块分类维护所有已对接/待对接接口。
 * <p>
 * URL 由配置 按主体和授权区域维护；本枚举只描述接口本身及所需授权类型。
 *
 * @author lenkor
 */
@Getter
public enum TemuApiType {

	// ================================================================
	// 一、订单相关 (ORDER) — US 端点
	// ================================================================

	/** 订单列表查询（v2版，已验证可用） */
	ORDER_LIST_V2_GET("bg.order.list.v2.get", AccountType.ORDER, Category.ORDER, "订单列表查询"),

	/** 订单详情查询 */
	ORDER_DETAIL_GET("bg.order.detail.get", AccountType.ORDER, Category.ORDER, "订单详情查询"),

	/** 订单收货地址查询（v2版，已验证可用） */
	ORDER_SHIPPINGINFO_V2_GET("bg.order.shippinginfo.v2.get", AccountType.ORDER, Category.ORDER, "订单收货地址查询"),

	/** 订单金额查询 */
	ORDER_AMOUNT_QUERY("bg.order.amount.query", AccountType.ORDER, Category.ORDER, "订单金额查询"),

	/** 合并发货列表查询 */
	ORDER_COMBINED_SHIPMENT_LIST_GET("bg.order.combinedshipment.list.get", AccountType.ORDER, Category.ORDER,
			"合并发货列表查询"),

	// ================================================================
	// 二、售后/退货退款 (AFTERSALES) — US 端点
	// ================================================================

	/** 售后单列表查询 */
	AFTERSALES_LIST_GET("bg.aftersales.aftersales.list.get", AccountType.ORDER, Category.AFTERSALES, "售后单列表查询"),

	/** 父售后单列表查询 */
	AFTERSALES_PARENT_LIST_GET("bg.aftersales.parentaftersales.list.get", AccountType.ORDER, Category.AFTERSALES,
			"父售后单列表查询"),

	/** 父售后单详情查询（补充原因、买家留言和子订单信息） */
	AFTERSALES_PARENT_DETAIL_GET("temu.aftersales.parentaftersales.detail.get", AccountType.ORDER, Category.AFTERSALES,
			"父售后单详情查询"),

	/** 退货物流查询 */
	AFTERSALES_PARENT_RETURN_ORDER_GET("bg.aftersales.parentreturnorder.get", AccountType.ORDER, Category.AFTERSALES,
			"退货物流查询"),

	/** 退货地址查询(已经不允许使用) */
	AFTERSALES_RETURN_ADDRESS_GET("temu.aftersales.returnaddress.get", AccountType.ORDER, Category.AFTERSALES,
			"退货地址查询"),

	// ================================================================
	// 三、物流发货 (LOGISTICS) — US 端点 半托自主发货接口
	// ================================================================

	/** 发货仓库列表查询 */
	LOGISTICS_WAREHOUSE_LIST_GET("bg.logistics.warehouse.list.get", AccountType.ORDER, Category.LOGISTICS, "发货仓库列表查询"),

	/** 物流公司列表查询 */
	LOGISTICS_COMPANIES_GET("bg.logistics.companies.get", AccountType.ORDER, Category.LOGISTICS, "物流公司列表查询"),

	/** 物流服务查询 */
	LOGISTICS_SHIPPING_SERVICES_GET("bg.logistics.shippingservices.get", AccountType.ORDER, Category.LOGISTICS,
			"物流服务查询"),

	/** 创建发货单 */
	LOGISTICS_SHIPMENT_CREATE("bg.logistics.shipment.create", AccountType.ORDER, Category.LOGISTICS, "创建发货单"),

	/** 发货单查询 */
	LOGISTICS_SHIPMENT_GET("bg.logistics.shipment.get", AccountType.ORDER, Category.LOGISTICS, "发货单查询"),

	/** 发货结果查询 */
	LOGISTICS_SHIPMENT_RESULT_GET("bg.logistics.shipment.result.get", AccountType.ORDER, Category.LOGISTICS, "发货结果查询"),

	/** 更新发货单 */
	LOGISTICS_SHIPMENT_UPDATE("bg.logistics.shipment.update", AccountType.ORDER, Category.LOGISTICS, "更新发货单"),

	/** 发货方式更新 */
	LOGISTICS_SHIPMENT_SHIPPING_TYPE_UPDATE("bg.logistics.shipment.shippingtype.update", AccountType.ORDER,
			Category.LOGISTICS, "发货方式更新"),

	/** 打印面单（链接10分钟有效） */
	LOGISTICS_SHIPMENT_DOCUMENT_GET("bg.logistics.shipment.document.get", AccountType.ORDER, Category.LOGISTICS,
			"打印面单"),

	/** 确认发货 */
	LOGISTICS_SHIPMENT_CONFIRM("bg.logistics.shipment.confirm", AccountType.ORDER, Category.LOGISTICS, "确认发货"),

	/** 子发货单确认 */
	LOGISTICS_SHIPMENT_SUB_CONFIRM("bg.logistics.shipment.sub.confirm", AccountType.ORDER, Category.LOGISTICS,
			"子发货单确认"),

	// ================================================================
	// 四、商品管理 (GOODS) — Partner 端点 (商品管理注意前缀，每个授权的端点前缀可能不同！，以下为通用，调用测试请核实使用)
	// ================================================================

	/** 商品列表查询 */
	LOCAL_GOODS_LIST_QUERY("bg.glo.goods.list.get", AccountType.PRODUCT, Category.GOODS, "商品列表查询"),

	/** SKU列表查询 */
	LOCAL_GOODS_SKU_LIST_QUERY("bg.local.goods.sku.list.query", AccountType.PRODUCT, Category.GOODS, "SKU列表查询"),

	/** SKU价格查询 */
	LOCAL_GOODS_SKU_PRICE_QUERY("bg.goods.price.list.get", AccountType.PRODUCT, Category.GOODS, "SKU价格查询"),

	/** 商品类目查询 */
	LOCAL_GOODS_CATS_GET("bg.local.goods.cats.get", AccountType.PRODUCT, Category.GOODS, "商品类目查询"),

	/** 商品属性查询 */
	LOCAL_GOODS_PROPERTY_GET("bg.local.goods.property.get", AccountType.PRODUCT, Category.GOODS, "商品属性查询"),

	/** 规格ID查询 */
	LOCAL_GOODS_SPEC_ID_GET("bg.local.goods.spec.id.get", AccountType.PRODUCT, Category.GOODS, "规格ID查询"),

	/** 尺寸元素查询 */
	LOCAL_GOODS_SIZE_ELEMENT_GET("bg.local.goods.size.element.get", AccountType.PRODUCT, Category.GOODS, "尺寸元素查询"),

	/** 商品模板查询 */
	LOCAL_GOODS_TEMPLATE_GET("bg.local.goods.template.get", AccountType.PRODUCT, Category.GOODS, "商品模板查询"),

	/** 品牌商标查询 */
	LOCAL_GOODS_BRAND_TRADEMARK_GET("bg.local.goods.brand.trademark.get", AccountType.PRODUCT, Category.GOODS,
			"品牌商标查询"),

	/** 类目推荐 */
	LOCAL_GOODS_CATEGORY_RECOMMEND("bg.local.goods.category.recommend", AccountType.PRODUCT, Category.GOODS, "类目推荐"),

	/** 商品添加 */
	LOCAL_GOODS_ADD("bg.glo.goods.add", AccountType.PRODUCT, Category.GOODS, "商品添加"),

	/** 商品更新 */
	LOCAL_GOODS_UPDATE("bg.glo.goods.update", AccountType.PRODUCT, Category.GOODS, "商品更新"),

	/** 商品部分更新 */
	LOCAL_GOODS_PARTIAL_UPDATE("bg.local.goods.partial.update", AccountType.PRODUCT, Category.GOODS, "商品部分更新"),

	/** 上下架状态设置 */
	LOCAL_GOODS_SALE_STATUS_SET("bg.glo.goods.sale.status.set", AccountType.PRODUCT, Category.GOODS, "上下架状态设置"),

	/** 商家外部编码设置 */
	LOCAL_GOODS_OUT_SN_SET("bg.glo.goods.out.sn.set", AccountType.PRODUCT, Category.GOODS, "商家外部编码设置"),

	/** 商家外部编码校验 */
	LOCAL_GOODS_OUT_SN_CHECK("bg.glo.goods.out.sn.check", AccountType.PRODUCT, Category.GOODS, "商家外部编码校验"),

	/** SKU外部编码设置 */
	LOCAL_GOODS_SKU_OUT_SN_SET("bg.glo.goods.sku.out.sn.set", AccountType.PRODUCT, Category.GOODS, "SKU外部编码设置"),

	/** SKU外部编码校验 */
	LOCAL_GOODS_SKU_OUT_SN_CHECK("bg.glo.goods.sku.out.sn.check", AccountType.PRODUCT, Category.GOODS, "SKU外部编码校验"),

	/** 合规商品列表查询 */
	LOCAL_COMPLIANCE_GOODS_LIST_QUERY("bg.glo.compliance.goods.list.query", AccountType.PRODUCT, Category.GOODS,
			"合规商品列表查询"),

	/** 运费模板列表查询 */
	FREIGHT_TEMPLATE_LIST_QUERY("bg.freight.template.list.query", AccountType.PRODUCT, Category.GOODS, "运费模板列表查询"),

	// ================================================================
	// 五、库存 — 实时库存 (STOCK) — Partner 端点 以下为全托模组（半托没有）
	// ================================================================

	/** 库存编辑 */
	LOCAL_GOODS_STOCK_EDIT("bg.btg.goods.stock.edit", AccountType.PRODUCT, Category.STOCK, "库存编辑"),

	/** 库存查询 */
	LOCAL_GOODS_STOCK_QUERY("bg.btg.goods.stock.query", AccountType.PRODUCT, Category.STOCK, "库存查询"),

	/** 库存获取 */
	LOCAL_GOODS_STOCK_GET("bg.btg.goods.stock.get", AccountType.PRODUCT, Category.STOCK, "库存获取"),

	/** 库存列表查询 */
	LOCAL_GOODS_STOCK_LIST_QUERY("bg.btg.goods.stock.list.query", AccountType.PRODUCT, Category.STOCK, "库存列表查询"),

	/** 库存数量查询 */
	LOCAL_GOODS_STOCK_QUANTITY_QUERY("bg.btg.goods.stock.quantity.query", AccountType.PRODUCT, Category.STOCK,
			"库存数量查询"),

	/** 库存数量获取 */
	LOCAL_GOODS_STOCK_QUANTITY_GET("bg.btg.goods.stock.quantity.get", AccountType.PRODUCT, Category.STOCK, "库存数量获取"),

	/** 库存数量列表查询 */
	LOCAL_GOODS_STOCK_QUANTITY_LIST_QUERY("bg.btg.goods.stock.quantity.list.query", AccountType.PRODUCT, Category.STOCK,
			"库存数量列表查询"),

	/** 虚拟库存明细查询 */
	LOCAL_GOODS_STOCK_DETAIL_QUERY("bg.btg.goods.stock.detail.query", AccountType.PRODUCT, Category.STOCK, "库存明细查询"),

	// ================================================================
	// 六、库存 — 库存流水 (STOCK_FLOW) — Partner 端点 以下为全托模组（半托没有）
	// ================================================================

	/** 库存流水查询 */
	LOCAL_GOODS_STOCK_FLOW_QUERY("bg.btg.goods.stock.flow.query", AccountType.PRODUCT, Category.STOCK_FLOW, "库存流水查询"),

	/** 库存流水列表查询 */
	LOCAL_GOODS_STOCK_FLOW_LIST_QUERY("bg.btg.goods.stock.flow.list.query", AccountType.PRODUCT, Category.STOCK_FLOW,
			"库存流水列表查询"),

	/** 库存记录查询 */
	LOCAL_GOODS_STOCK_RECORD_QUERY("bg.btg.goods.stock.record.query", AccountType.PRODUCT, Category.STOCK_FLOW,
			"库存记录查询"),

	/** 库存交易查询 */
	LOCAL_GOODS_STOCK_TRANSACTION_QUERY("bg.btg.goods.stock.transaction.query", AccountType.PRODUCT,
			Category.STOCK_FLOW, "库存交易查询"),

	/** 库存变更查询 */
	LOCAL_GOODS_STOCK_CHANGE_QUERY("bg.btg.goods.stock.change.query", AccountType.PRODUCT, Category.STOCK_FLOW,
			"库存变更查询"),

	// ================================================================
	// 七、入库单 (INBOUND) — Partner 端点 以下为全托模组（半托没有）
	// ================================================================

	/** 入库查询 */
	LOCAL_GOODS_INBOUND_QUERY("bg.btg.goods.inbound.query", AccountType.PRODUCT, Category.INBOUND, "入库查询"),

	/** 入库列表查询 */
	LOCAL_GOODS_INBOUND_LIST_QUERY("bg.btg.goods.inbound.list.query", AccountType.PRODUCT, Category.INBOUND, "入库列表查询"),

	/** 入库详情获取 */
	LOCAL_GOODS_INBOUND_GET("bg.btg.goods.inbound.get", AccountType.PRODUCT, Category.INBOUND, "入库详情获取"),

	/** 入库单查询 */
	LOCAL_GOODS_INBOUND_ORDER_QUERY("bg.btg.goods.inbound.order.query", AccountType.PRODUCT, Category.INBOUND, "入库单查询"),

	/** 入库单列表查询 */
	LOCAL_GOODS_INBOUND_ORDER_LIST_QUERY("bg.btg.goods.inbound.order.list.query", AccountType.PRODUCT, Category.INBOUND,
			"入库单列表查询"),

	/** 入库计划查询 */
	LOCAL_GOODS_INBOUND_PLAN_QUERY("bg.btg.goods.inbound.plan.query", AccountType.PRODUCT, Category.INBOUND, "入库计划查询"),

	/** 入库计划列表查询 */
	LOCAL_GOODS_INBOUND_PLAN_LIST_QUERY("bg.btg.goods.inbound.plan.list.query", AccountType.PRODUCT, Category.INBOUND,
			"入库计划列表查询"),

	/** 退货入库查询 */
	LOCAL_GOODS_INBOUND_RETURN_QUERY("bg.btg.goods.inbound.return.query", AccountType.PRODUCT, Category.INBOUND,
			"退货入库查询"),

	/** 退货入库列表查询 */
	LOCAL_GOODS_INBOUND_RETURN_LIST_QUERY("bg.btg.goods.inbound.return.list.query", AccountType.PRODUCT,
			Category.INBOUND, "退货入库列表查询"),

	// ================================================================
	// 八、出库单 (OUTBOUND) — Partner 端点 以下为全托模组（半托没有）
	// ================================================================

	/** 出库查询 */
	LOCAL_GOODS_OUTBOUND_QUERY("bg.btg.goods.outbound.query", AccountType.PRODUCT, Category.OUTBOUND, "出库查询"),

	/** 出库列表查询 */
	LOCAL_GOODS_OUTBOUND_LIST_QUERY("bg.btg.goods.outbound.list.query", AccountType.PRODUCT, Category.OUTBOUND,
			"出库列表查询"),

	/** 出库详情获取 */
	LOCAL_GOODS_OUTBOUND_GET("bg.btg.goods.outbound.get", AccountType.PRODUCT, Category.OUTBOUND, "出库详情获取"),

	/** 出库单查询 */
	LOCAL_GOODS_OUTBOUND_ORDER_QUERY("bg.btg.goods.outbound.order.query", AccountType.PRODUCT, Category.OUTBOUND,
			"出库单查询"),

	/** 出库单列表查询 */
	LOCAL_GOODS_OUTBOUND_ORDER_LIST_QUERY("bg.btg.goods.outbound.order.list.query", AccountType.PRODUCT,
			Category.OUTBOUND, "出库单列表查询"),

	/** 移除出库查询 */
	LOCAL_GOODS_OUTBOUND_REMOVE_QUERY("bg.btg.goods.outbound.remove.query", AccountType.PRODUCT, Category.OUTBOUND,
			"移除出库查询"),

	/** 移除出库列表查询 */
	LOCAL_GOODS_OUTBOUND_REMOVE_LIST_QUERY("bg.btg.goods.outbound.remove.list.query", AccountType.PRODUCT,
			Category.OUTBOUND, "移除出库列表查询"),

	/** 报废出库查询 */
	LOCAL_GOODS_OUTBOUND_SCRAP_QUERY("bg.btg.goods.outbound.scrap.query", AccountType.PRODUCT, Category.OUTBOUND,
			"报废出库查询"),

	/** 报废出库列表查询 */
	LOCAL_GOODS_OUTBOUND_SCRAP_LIST_QUERY("bg.btg.goods.outbound.scrap.list.query", AccountType.PRODUCT,
			Category.OUTBOUND, "报废出库列表查询"),

	/** 销毁出库查询 */
	LOCAL_GOODS_OUTBOUND_DESTROY_QUERY("bg.btg.goods.outbound.destroy.query", AccountType.PRODUCT, Category.OUTBOUND,
			"销毁出库查询"),

	/** 销毁出库列表查询 */
	LOCAL_GOODS_OUTBOUND_DESTROY_LIST_QUERY("bg.btg.goods.outbound.destroy.list.query", AccountType.PRODUCT,
			Category.OUTBOUND, "销毁出库列表查询"),

	// ================================================================
	// 十二、授权/Token (TOKEN) — US 端点
	// ================================================================

	/** Token信息查询（含有效期） */
	ACCESSTOKEN_INFO_GET("bg.open.accesstoken.info.get", AccountType.ORDER, Category.TOKEN, "Token信息查询"),

	// ================================================================
	// 十三、消息订阅 (MESSAGE) — US 端点
	// ================================================================

	/** 消息更新 */
	TMC_MESSAGE_UPDATE("bg.tmc.message.update", AccountType.ORDER, Category.MESSAGE, "消息更新");

	// ================================================================
	// 字段定义
	// ================================================================

	/** 接口 type 值（传给 Temu API 的参数） */
	private final String type;

	/** 所需授权类型：商品或订单。 */
	private final AccountType accountType;

	/** 业务分类 */
	private final Category category;

	/** 中文描述 */
	private final String desc;

	private static final Map<String, TemuApiType> TYPE_MAP = new HashMap<>();

	static {
		for (TemuApiType api : values()) {
			TYPE_MAP.put(api.type, api);
		}
	}

	TemuApiType(String type, AccountType accountType, Category category, String desc) {
		this.type = type;
		this.accountType = accountType;
		this.category = category;
		this.desc = desc;
	}

	/** 按 type 字符串查询枚举 */
	public static TemuApiType fromType(String type) {
		return type == null ? null : TYPE_MAP.get(type);
	}

	/** Temu 授权账号类型，对应 tb_temu_access_token.type。 */
	@Getter
	public enum AccountType {

		PRODUCT(0, "商品"), ORDER(1, "订单");

		private final int code;

		private final String desc;

		AccountType(int code, String desc) {
			this.code = code;
			this.desc = desc;
		}

	}

	/**
	 * 接口业务分类
	 */
	@Getter
	public enum Category {

		/** 订单 */
		ORDER("订单"),
		/** 售后/退货退款 */
		AFTERSALES("售后/退货退款"),
		/** 物流发货 */
		LOGISTICS("物流发货"),
		/** 商品管理 */
		GOODS("商品管理"),
		/** 库存 — 实时库存 */
		STOCK("库存"),
		/** 库存 — 库存流水 */
		STOCK_FLOW("库存流水"),
		/** 入库单 */
		INBOUND("入库单"),
		/** 出库单 */
		OUTBOUND("出库单"),
		/** 费用数据 */
		FEE("费用数据"),
		/** 结算数据 */
		SETTLEMENT("结算数据"),
		/** 提款数据 */
		WITHDRAW("提款数据"),
		/** 授权/Token */
		TOKEN("授权/Token"),
		/** 消息订阅 */
		MESSAGE("消息订阅");

		private final String desc;

		Category(String desc) {
			this.desc = desc;
		}

	}

}
