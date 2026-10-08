package com.badfisher.temu.client.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Temu authorization/API routing region, independent from the store business site. */
@Getter
public enum TemuAuthRegion {

	US("US", "美国授权区"), GLOBAL("GLOBAL", "Global授权区"), EU("EU", "欧洲授权区"), CN("CN", "商品授权区");

	private static final Map<String, TemuAuthRegion> CODE_MAP = new HashMap<>();

	static {
		for (TemuAuthRegion region : values()) {
			CODE_MAP.put(region.code, region);
		}
	}

	private final String code;

	private final String desc;

	TemuAuthRegion(String code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	public static TemuAuthRegion fromCode(String code) {
		return code == null ? null : CODE_MAP.get(code.trim().toUpperCase(Locale.ROOT));
	}

	/** Legacy migration fallback only. New code should persist authRegion explicitly. */
	public static TemuAuthRegion fromBusinessSite(String site) {
		if (site == null)
			return null;
		switch (site.trim().toUpperCase(Locale.ROOT)) {
			case "US":
				return US;
			case "CA":
			case "AU":
			case "GLOBAL":
				return GLOBAL;
			case "UK":
			case "EU":
				return EU;
			case "CN":
				return CN;
			default:
				return null;
		}
	}

}
