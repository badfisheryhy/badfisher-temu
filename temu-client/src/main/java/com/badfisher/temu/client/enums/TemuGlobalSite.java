package com.badfisher.temu.client.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Temu Global 订单、售后接口业务站点。
 *
 * <p>
 * 该枚举描述 Global 接口返回的 {@code siteId}，与用于授权查询及 API 地址路由的 {@link TemuAuthRegion} 相互独立，避免把
 * Global 业务站点 和授权表中的站点概念混用。
 * </p>
 *
 * @author lenkor
 */
@Getter
public enum TemuGlobalSite {

	US(100, "us", "美国站", true), CA(101, "ca", "加拿大站", true), UK(102, "uk", "英国站", true), AU(103, "au", "澳大利亚站", true),
	NZ(104, "nz", "新西兰站", true), DE(105, "de", "德国站", true), FR(106, "fr", "法国站", true), IT(107, "it", "意大利站", true),
	NL(108, "nl", "荷兰站", true), ES(109, "es", "西班牙站", true), MX(110, "mx", "墨西哥站", true), PT(111, "pt", "葡萄牙站", true),
	PL(112, "pl", "波兰站", true), SE(113, "se", "瑞典站", true), CH(114, "ch", "瑞士站", true), GR(115, "gr", "希腊", true),
	IE(116, "ie", "爱尔兰", true), CY(117, "cy", "塞浦路斯", true), JP(118, "jp", "日本站", true), KR(119, "kr", "韩国站", false),
	SA(120, "sa", "沙特站", true), SG(121, "sg", "新加坡站", false), AE(122, "ae", "阿联酋站", true), KW(123, "kw", "科威特站", true),
	NO(124, "no", "挪威站", true), CL(125, "cl", "智利站", true), MY(126, "my", "马来西亚站", true), PH(127, "ph", "菲律宾站", true),
	TW(128, "tw", "中国台湾站", false), TH(129, "th", "泰国站", true), QA(130, "qa", "卡塔尔站", true), JO(131, "jo", "约旦站", true),
	BR(132, "br", "巴西站", false), OM(133, "om", "阿曼站", true), BH(134, "bh", "巴林站", true), IL(135, "il", "以色列站", true),
	ZA(136, "za", "南非站", true), CZ(137, "cz", "捷克站", true), HU(138, "hu", "匈牙利站", true), DK(139, "dk", "丹麦站", true),
	RO(140, "ro", "罗马尼亚站", true), BG(141, "bg", "保加利亚站", true), BE(142, "be", "比利时站", true),
	AT(143, "at", "奥地利站", true), FI(144, "fi", "芬兰站", true), SK(145, "sk", "斯洛伐克站", true), HR(146, "hr", "克罗地亚站", true),
	SI(147, "si", "斯洛文尼亚站", true), LT(148, "lt", "立陶宛站", true), EE(149, "ee", "爱沙尼亚站", true),
	LV(150, "lv", "拉脱维亚站", true), MT(151, "mt", "马耳他站", true), LU(152, "lu", "卢森堡站", true), RS(153, "rs", "塞尔维亚", true),
	MD(154, "md", "摩尔多瓦", true), ME(155, "me", "黑山", true), IS(156, "is", "冰岛", true), AD(157, "ad", "安道尔", false),
	BA(158, "ba", "波黑", true), AL(159, "al", "阿尔巴尼亚", true), XK(161, "xk", "科索沃", false), KZ(162, "kz", "哈萨克斯坦", false),
	PE(163, "pe", "秘鲁", true), CO(164, "co", "哥伦比亚", true), GE(165, "ge", "格鲁吉亚", true), AM(166, "am", "亚美尼亚", true),
	AZ(167, "az", "阿塞拜疆", true), UA(168, "ua", "乌克兰", false), UY(169, "uy", "乌拉圭", false), MU(170, "mu", "毛里求斯", true),
	MA(171, "ma", "摩洛哥", true), DO(172, "do", "多米尼加", false), CR(173, "cr", "哥斯达黎加", false),
	TR(174, "tr", "土耳其", false), DZ(175, "dz", "阿尔及利亚站", false), PA(176, "pa", "巴拿马站", true),
	KE(177, "ke", "肯尼亚站", false), EC(178, "ec", "厄瓜多尔站", true), TT(179, "tt", "特立尼达和多巴哥站", false),
	GT(180, "gt", "危地马拉站", false), UZ(181, "uz", "乌兹别克斯坦站", false), HN(182, "hn", "洪都拉斯站", false),
	SV(183, "sv", "萨尔瓦多站", false), PK(184, "pk", "巴基斯坦站", false), LK(185, "lk", "斯里兰卡站", true),
	MN(186, "mn", "蒙古站", false), VN(187, "vn", "越南站", true), BN(188, "bn", "文莱站", true), AR(189, "ar", "阿根廷站", false),
	NG(190, "ng", "尼日利亚站", false), KH(191, "kh", "柬埔寨站", false), MK(192, "mk", "北马其顿", true),
	BD(193, "bd", "孟加拉站", false), KG(194, "kg", "吉尔吉斯斯坦站", true), MV(196, "mv", "马尔代夫站", false),
	LI(197, "li", "列支敦士登站", true);

	private static final Map<Integer, TemuGlobalSite> SITE_ID_MAP = new HashMap<>();

	private static final Map<String, TemuGlobalSite> CODE_MAP = new HashMap<>();

	private static final Map<String, TemuGlobalSite> SITE_NAME_MAP = new HashMap<>();

	static {
		for (TemuGlobalSite site : values()) {
			SITE_ID_MAP.put(site.siteId, site);
			CODE_MAP.put(site.code, site);
			SITE_NAME_MAP.put(site.siteName, site);
		}
	}

	/** Global 接口返回的站点ID。 */
	private final Integer siteId;

	/** 业务简写，用于日志、参数及数据区分。 */
	private final String code;

	/** Temu站点中文名称。 */
	private final String siteName;

	/** 当前是否已经开站。关闭站点仍保留枚举映射，用于识别平台返回的历史或异常数据。 */
	private final boolean open;

	TemuGlobalSite(Integer siteId, String code, String siteName, boolean open) {
		this.siteId = siteId;
		this.code = code;
		this.siteName = siteName;
		this.open = open;
	}

	/**
	 * 根据 Global 接口的站点ID获取枚举。
	 * @param siteId 站点ID
	 * @return 对应站点，无法识别时返回 {@code null}
	 */
	public static TemuGlobalSite fromSiteId(Integer siteId) {
		return siteId == null ? null : SITE_ID_MAP.get(siteId);
	}

	/**
	 * 根据站点简写获取枚举，不区分大小写。
	 * @param code 站点简写，例如 us、ca、uk、au
	 * @return 对应站点，无法识别时返回 {@code null}
	 */
	public static TemuGlobalSite fromCode(String code) {
		if (code == null) {
			return null;
		}
		return CODE_MAP.get(code.trim().toLowerCase(Locale.ROOT));
	}

	/**
	 * 根据站点中文名称获取枚举。
	 * @param siteName 站点名称
	 * @return 对应站点，无法识别时返回 {@code null}
	 */
	public static TemuGlobalSite fromSiteName(String siteName) {
		return siteName == null ? null : SITE_NAME_MAP.get(siteName.trim());
	}

}
