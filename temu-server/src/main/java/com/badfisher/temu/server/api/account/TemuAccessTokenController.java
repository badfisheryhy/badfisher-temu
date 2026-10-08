package com.badfisher.temu.server.api.account;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.badfisher.temu.business.account.dto.TemuAccessTokenQueryDTO;
import com.badfisher.temu.business.account.dto.TemuAccessTokenUpdateDTO;
import com.badfisher.temu.business.account.dto.TemuAccessTokenUpdateResultDTO;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.server.web.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.stream.Collectors;

/**
 * Temu 授权 Token 管理控制器 — 提供Token刷新及有效期维护能力。
 *
 * @author lenkor
 */
@Slf4j
@Api(tags = "Temu Token管理")
@RestController
@RequestMapping("/temu/access-token")
public class TemuAccessTokenController {

	@Resource
	private TemuAccessTokenService temuAccessTokenService;

	/** 分页查询有效授权记录，响应中不包含Token及应用密钥等敏感信息。 */
	@GetMapping("/list")
	@ApiOperation(value = "分页查询Temu Token", notes = "支持按App ID、渠道、卖家、店铺名称、站点和授权类型查询；不会返回accessToken、appKey和appSecret。")
	public Result<IPage<TemuAccessTokenUpdateResultDTO>> listTokens(TemuAccessTokenQueryDTO query) {
		int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
		int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 20 : Math.min(query.getPageSize(), 200);

		Page<TemuAccessToken> tokenPage = new Page<>(pageNum, pageSize);
		temuAccessTokenService.page(tokenPage,
				Wrappers.lambdaQuery(TemuAccessToken.class)
					.eq(hasText(query.getAppId()), TemuAccessToken::getAppId, query.getAppId())
					.eq(query.getChannelId() != null, TemuAccessToken::getChannelId, query.getChannelId())
					.eq(hasText(query.getSellerId()), TemuAccessToken::getSellerId, query.getSellerId())
					.like(hasText(query.getShopName()), TemuAccessToken::getShopName, query.getShopName())
					.eq(hasText(query.getSite()), TemuAccessToken::getSite, query.getSite())
					.eq(hasText(query.getAuthRegion()), TemuAccessToken::getAuthRegion, query.getAuthRegion())
					.eq(query.getType() != null, TemuAccessToken::getType, query.getType())
					.orderByDesc(TemuAccessToken::getUpdateTime)
					.orderByDesc(TemuAccessToken::getId));

		Page<TemuAccessTokenUpdateResultDTO> resultPage = new Page<>(tokenPage.getCurrent(), tokenPage.getSize(),
				tokenPage.getTotal());
		resultPage.setRecords(tokenPage.getRecords().stream().map(this::toResult).collect(Collectors.toList()));
		return Result.success(resultPage);
	}

	/**
	 * 更新Token时以服务器当前时间作为有效期起点：expireTime = 当前时间 + expireDays。
	 * 响应不会返回accessToken、appKey和appSecret等敏感信息。
	 */
	@PutMapping("/update")
	@ApiOperation(value = "更新Temu Token", notes = "根据授权记录ID更新accessToken；过期日期按服务器当前时间加有效天数自动计算。")
	public Result<TemuAccessTokenUpdateResultDTO> updateToken(
			@ApiParam(value = "Token更新参数", required = true) @RequestBody TemuAccessTokenUpdateDTO dto) {
		try {
			TemuAccessToken token = temuAccessTokenService.updateAccessToken(dto);
			log.info("Temu Token更新成功: id={}, appId={}, sellerId={}, expireTime={}", token.getId(), token.getAppId(),
					token.getSellerId(), token.getExpireTime());
			return Result.success("Token更新成功", toResult(token));
		}
		catch (IllegalArgumentException e) {
			log.warn("Temu Token更新参数错误: {}", e.getMessage());
			return Result.fail(400, e.getMessage());
		}
		catch (Exception e) {
			log.error("Temu Token更新失败: id={}", dto != null ? dto.getId() : null, e);
			return Result.fail("Token更新失败: " + e.getMessage());
		}
	}

	private TemuAccessTokenUpdateResultDTO toResult(TemuAccessToken token) {
		TemuAccessTokenUpdateResultDTO result = new TemuAccessTokenUpdateResultDTO();
		result.setId(token.getId());
		result.setAppId(token.getAppId());
		result.setChannelId(token.getChannelId());
		result.setSellerId(token.getSellerId());
		result.setShopName(token.getShopName());
		result.setSite(token.getSite());
		result.setAuthRegion(token.getAuthRegion());
		result.setType(token.getType());
		result.setExpireDays(token.getExpireDays());
		result.setExpireTime(token.getExpireTime());
		result.setCreateTime(token.getCreateTime());
		result.setUpdateTime(token.getUpdateTime());
		result.setExpired(token.isExpired());
		return result;
	}

	private boolean hasText(String value) {
		return value != null && !value.trim().isEmpty();
	}

}
