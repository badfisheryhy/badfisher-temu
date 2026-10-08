package com.badfisher.temu.business.account.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.client.enums.TemuGlobalSite;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Resolves a Global response siteId to the ERP store owning that row. */
@Service
public class TemuGlobalAccountResolver {

	@Resource
	private TemuAccessTokenService temuAccessTokenService;

	public Map<Integer, TemuAccessToken> buildSiteAccountMap(TemuAccessToken requestAccount) {
		List<TemuAccessToken> accounts = temuAccessTokenService.list(Wrappers.lambdaQuery(TemuAccessToken.class)
			.eq(TemuAccessToken::getAppId, requestAccount.getAppId())
			.eq(TemuAccessToken::getSellerId, requestAccount.getSellerId())
			.eq(TemuAccessToken::getAuthRegion, requestAccount.getAuthRegion())
			.eq(TemuAccessToken::getType, requestAccount.getType())
			.eq(TemuAccessToken::getDelFlag, 0));

		Map<Integer, TemuAccessToken> result = new LinkedHashMap<>();
		for (TemuAccessToken account : accounts) {
			TemuGlobalSite globalSite = TemuGlobalSite.fromCode(account.getSite());
			if (globalSite == null) {
				continue;
			}
			if (account.getChannelId() == null) {
				throw new IllegalStateException("Global区域授权缺少channelId: appId=" + requestAccount.getAppId()
						+ ", sellerId=" + requestAccount.getSellerId() + ", site=" + account.getSite());
			}
			TemuAccessToken previous = result.putIfAbsent(globalSite.getSiteId(), account);
			if (previous != null) {
				throw new IllegalStateException("Global站点存在多条有效授权: appId=" + requestAccount.getAppId() + ", sellerId="
						+ requestAccount.getSellerId() + ", site=" + globalSite.getCode().toUpperCase(Locale.ROOT)
						+ ", channelIds=" + previous.getChannelId() + "," + account.getChannelId());
			}
		}
		return result;
	}

	public TemuAccessToken resolve(Integer siteId, Map<Integer, TemuAccessToken> siteAccountMap,
			TemuAccessToken requestAccount, String businessKey) {
		TemuGlobalSite globalSite = TemuGlobalSite.fromSiteId(siteId);
		if (globalSite == null) {
			throw new IllegalStateException("Global数据站点无法识别: siteId=" + siteId + ", sellerId="
					+ requestAccount.getSellerId() + ", businessKey=" + businessKey);
		}
		return resolve(globalSite, siteAccountMap, requestAccount, businessKey);
	}

	public TemuAccessToken resolveBySite(String site, Map<Integer, TemuAccessToken> siteAccountMap,
			TemuAccessToken requestAccount, String businessKey) {
		TemuGlobalSite globalSite = TemuGlobalSite.fromCode(site);
		if (globalSite == null) {
			throw new IllegalStateException("Global数据站点无法识别: site=" + site + ", sellerId="
					+ requestAccount.getSellerId() + ", businessKey=" + businessKey);
		}
		return resolve(globalSite, siteAccountMap, requestAccount, businessKey);
	}

	private TemuAccessToken resolve(TemuGlobalSite globalSite, Map<Integer, TemuAccessToken> siteAccountMap,
			TemuAccessToken requestAccount, String businessKey) {
		TemuAccessToken target = siteAccountMap.get(globalSite.getSiteId());
		if (target == null || target.getChannelId() == null) {
			throw new IllegalStateException("Global数据缺少有效站点映射: appId=" + requestAccount.getAppId()
					+ ", sellerId=" + requestAccount.getSellerId() + ", site=" + globalSite.getCode()
					+ ", businessKey=" + businessKey);
		}
		return target;
	}

}
