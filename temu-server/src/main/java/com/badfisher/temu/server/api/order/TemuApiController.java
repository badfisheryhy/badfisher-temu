package com.badfisher.temu.server.api.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.badfisher.temu.client.TemuApiClient;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.server.web.Result;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.badfisher.temu.client.util.CommonUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Temu API 通用调用控制器 — 通过 HTTP 请求外部测试任意 Temu 接口
 * <p>
 * 用法：POST /temu/api/call，body 传入业务参数 + type，自动补全公共参数和签名。 <pre>
 * 标准请求格式（店铺路由参数不会透传给 Temu）:
 * {
 *   "type": "temu.track.trackinginfo.get",
 *   "sellerId": "demo-seller",
 *   "site": "US",
 *   "accountType": 1,
 *   "parentOrderSn": "DEMO-ORDER-001"
 * }
 * </pre>
 *
 * @author lenkor
 */
@Slf4j
@Api(tags = "Temu API 通用调用")
@RestController
@ConditionalOnProperty(prefix = "temu.api", name = "debug-enabled", havingValue = "true")
@RequestMapping("/temu/api")
public class TemuApiController {

	private static final String CONTROL_APP_ID = "appId";

	private static final String CONTROL_SELLER_ID = "sellerId";

	private static final String CONTROL_SITE = "site";

	private static final String CONTROL_AUTH_REGION = "authRegion";

	private static final String CONTROL_ACCOUNT_TYPE = "accountType";

	@Resource
	private TemuApiClient temuApiClient;

	@Resource
	private TemuAccessTokenService temuAccessTokenService;

	/**
	 * 通用 API 调用 — 传入业务参数和 type，自动签名并调用 Temu API
	 * <p>
	 * 路由控制参数（不会传给 Temu）:
	 * <ul>
	 * <li>appId: 指定 Temu 应用，可选</li>
	 * <li>sellerId: 指定卖家授权主体</li>
	 * <li>site: 业务站点，优先按卖家、站点和账号类型匹配授权记录</li>
	 * <li>authRegion: 显式指定授权/API路由区域 US/GLOBAL/EU/CN，可选</li>
	 * <li>accountType: 授权类型，0=商品、1=订单，默认1</li>
	 * </ul>
	 */
	@PostMapping("/call")
	@ApiOperation(value = "通用 API 调用",
			notes = "传入type和业务参数，自动选择授权、签名并调用Temu。路由控制参数：appId、sellerId、site、authRegion、accountType，这些参数不会透传给Temu")
	public Result<Map<String, Object>> callApi(@ApiParam(value = "必须包含type；sellerId/site/accountType用于选择授权且不会透传给Temu",
			required = true) @RequestBody Map<String, Object> param) {
		if (param == null) {
			return Result.fail("请求参数不能为空");
		}

		// 提取控制参数
		String appId = controlText(param, CONTROL_APP_ID);
		String sellerId = controlText(param, CONTROL_SELLER_ID);
		String site = controlText(param, CONTROL_SITE);
		if (site != null) {
			site = site.toUpperCase(Locale.ROOT);
		}
		String authRegionCode = controlText(param, CONTROL_AUTH_REGION);
		TemuAuthRegion requestedAuthRegion = authRegionCode == null ? null : TemuAuthRegion.fromCode(authRegionCode);
		if (authRegionCode != null && requestedAuthRegion == null) {
			return Result.fail("控制参数 authRegion 只支持 US/GLOBAL/EU/CN");
		}
		// 未指定站点和授权区域时，默认使用US订单授权。
		if (site == null && requestedAuthRegion == null) {
			requestedAuthRegion = TemuAuthRegion.US;
		}

		Integer accountType;
		try {
			Object accountTypeValue = param.get(CONTROL_ACCOUNT_TYPE);
			accountType = accountTypeValue != null ? Integer.parseInt(accountTypeValue.toString().trim()) : 1;
		}
		catch (NumberFormatException e) {
			return Result.fail("控制参数 accountType 必须为 0 或 1");
		}
		if (accountType != 0 && accountType != 1) {
			return Result.fail("控制参数 accountType 必须为 0 或 1");
		}
		if (accountType == 0 && requestedAuthRegion != null && requestedAuthRegion != TemuAuthRegion.CN) {
			return Result.fail("商品授权(accountType=0)必须使用 authRegion=CN");
		}

		// 移除控制参数，剩余的作为业务参数
		Map<String, Object> businessParams = new HashMap<>(param);
		businessParams.remove(CONTROL_APP_ID);
		businessParams.remove(CONTROL_SELLER_ID);
		businessParams.remove(CONTROL_AUTH_REGION);
		businessParams.remove(CONTROL_ACCOUNT_TYPE);
		businessParams.remove(CONTROL_SITE);

		businessParams = CommonUtils.removeNullValue(businessParams);
		if (businessParams.get("type") == null) {
			return Result.fail("缺少必要参数: type");
		}

		// 按请求身份查找授权；GLOBAL 下 AU/CA 映射共用同一授权，固定取最早记录调用一次。
		TemuAccessToken token = findToken(appId, sellerId, site,
				requestedAuthRegion == null ? null : requestedAuthRegion.getCode(), accountType);
		if (token == null) {
			return Result.fail("未找到店铺凭据: appId=" + appId + ", sellerId=" + sellerId + ", site=" + site + ", authRegion="
					+ authRegionCode + ", accountType=" + accountType);
		}

		TemuAuthRegion authRegion = requestedAuthRegion != null ? requestedAuthRegion
				: TemuAuthRegion.fromCode(token.getAuthRegion());
		if (authRegion == null) {
			return Result.fail("店铺凭据的authRegion无效: tokenId=" + token.getId() + ", authRegion=" + token.getAuthRegion());
		}
		if (accountType == 0 && authRegion != TemuAuthRegion.CN) {
			return Result.fail("商品授权(accountType=0)必须使用 authRegion=CN");
		}

		log.info("API 调用: type={}, appId={}, sellerId={}, authRegion={}, accountType={}", businessParams.get("type"),
				token.getAppId(), token.getSellerId(), authRegion.getCode(), token.getType());
		log.info("请求参数: {}", businessParams);

		try {
			Map<String, Object> response = temuApiClient.callByAuthRegion(authRegion, businessParams, token);
			return Result.success(response);
		}
		catch (Exception e) {
			log.error("API 调用失败: type={}", businessParams.get("type"), e);
			return Result.fail(e.getMessage());
		}
	}

	/**
	 * 按授权请求身份查找凭据，未指定 appId/sellerId 时取该授权区第一条。
	 * @param appId Temu应用ID（可选）
	 * @param sellerId 卖家ID（可选）
	 * @param site 业务站点（可选）
	 * @param authRegion 授权/API路由区域（可选，不传时从命中的授权记录读取）
	 * @param type 账号类型（0=商品, 1=订单）
	 */
	private TemuAccessToken findToken(String appId, String sellerId, String site, String authRegion, Integer type) {
		List<TemuAccessToken> tokens = temuAccessTokenService.list(Wrappers.lambdaQuery(TemuAccessToken.class)
			.eq(appId != null, TemuAccessToken::getAppId, appId)
			.eq(sellerId != null, TemuAccessToken::getSellerId, sellerId)
			.eq(site != null, TemuAccessToken::getSite, site)
			.eq(authRegion != null, TemuAccessToken::getAuthRegion, authRegion)
			.eq(TemuAccessToken::getType, type)
			.eq(TemuAccessToken::getDelFlag, 0)
			.orderByAsc(TemuAccessToken::getId));
		return tokens.isEmpty() ? null : tokens.get(0);
	}

	private static String controlText(Map<String, Object> param, String key) {
		Object value = param.get(key);
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value).trim();
		return text.isEmpty() ? null : text;
	}

}
