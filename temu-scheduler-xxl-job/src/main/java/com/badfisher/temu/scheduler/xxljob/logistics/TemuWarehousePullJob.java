package com.badfisher.temu.scheduler.xxljob.logistics;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.business.warehouse.service.TemuWarehousePullService;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.badfisher.temu.business.sync.enums.TemuPullType;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.badfisher.temu.scheduler.xxljob.TemuXxlJobResult.run;

/**
 * Temu 仓库拉取任务 — 调用 bg.logistics.warehouse.list.get 同步发货仓库列表到 tb_temu_warehouse
 * <p>
 * 该接口无分页/时间窗口，一次调用返回授权主体的全量仓库，故单独成Job而非走任务窗口流程。 按appId + sellerId + authRegion +
 * type去重请求；仓库数据不按业务site或channel拆分。
 *
 * @author lenkor
 */
@Slf4j
@Component
public class TemuWarehousePullJob {

	@Resource
	private TemuAccessTokenService temuAccessTokenService;

	@Resource
	private TemuWarehousePullService temuWarehousePullService;

	@XxlJob("temuUsWarehousePullJob")
	public void executeUs() {
		run("Temu US仓库拉取任务", () -> executeForAuthRegion(TemuAuthRegion.US));
	}

	@XxlJob("temuGlobalWarehousePullJob")
	public void executeGlobal() {
		run("Temu GLOBAL仓库拉取任务", () -> executeForAuthRegion(TemuAuthRegion.GLOBAL));
	}

	@XxlJob("temuEuWarehousePullJob")
	public void executeEu() {
		run("Temu EU仓库拉取任务", () -> executeForAuthRegion(TemuAuthRegion.EU));
	}

	void executeForAuthRegion(TemuAuthRegion authRegion) {
		log.info("====== Temu 仓库拉取任务({}) 开始 ======", authRegion.getCode());

		List<TemuAccessToken> accounts = temuAccessTokenService.list(Wrappers.lambdaQuery(TemuAccessToken.class)
			.eq(TemuAccessToken::getAuthRegion, authRegion.getCode())
			.eq(TemuAccessToken::getType, TemuPullType.ORDER.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.orderByAsc(TemuAccessToken::getId));
		if (accounts.isEmpty()) {
			log.info("无{}授权区域订单账号，跳过仓库同步", authRegion.getCode());
			return;
		}

		Map<String, List<TemuAccessToken>> groups = new LinkedHashMap<>();
		for (TemuAccessToken account : accounts) {
			String key = requestIdentity(account);
			groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(account);
		}

		int total = 0;
		int successGroups = 0;
		for (Map.Entry<String, List<TemuAccessToken>> entry : groups.entrySet()) {
			List<TemuAccessToken> mappings = entry.getValue();
			TemuAccessToken requestAccount = mappings.get(0);
			String sellerId = requestAccount.getSellerId();
			try {
				validateMappings(entry.getKey(), mappings);
			}
			catch (Exception e) {
				log.error("仓库授权映射校验失败: identity={}, error={}", entry.getKey(), e.getMessage());
				continue;
			}
			if (requestAccount.getAppKey() == null || requestAccount.getAppSecret() == null
					|| requestAccount.getAccessToken() == null) {
				log.warn("店铺 {} 授权信息不完整, 跳过", sellerId);
				continue;
			}
			if (requestAccount.isExpired()) {
				log.warn("店铺 {} accessToken 已过期, 跳过仓库拉取", sellerId);
				continue;
			}

			try {
				int n = temuWarehousePullService.doPullTask(requestAccount, authRegion);
				if (n >= 0) {
					total += n;
					successGroups++;
				}
			}
			catch (Exception e) {
				log.error("店铺 {} 仓库拉取失败", sellerId, e);
			}
		}

		log.info("====== Temu 仓库拉取任务({}) 完成 | 请求主体={}, 成功主体={}, 共落库={} ======", authRegion.getCode(), groups.size(),
				successGroups, total);
	}

	private static String requestIdentity(TemuAccessToken account) {
		return account.getAppId() + "|" + account.getSellerId() + "|" + account.getAuthRegion() + "|"
				+ account.getType();
	}

	private static void validateMappings(String identity, List<TemuAccessToken> mappings) {
		TemuAccessToken first = mappings.get(0);
		for (TemuAccessToken mapping : mappings) {
			if (!Objects.equals(first.getAppKey(), mapping.getAppKey())
					|| !Objects.equals(first.getAppSecret(), mapping.getAppSecret())
					|| !Objects.equals(first.getAccessToken(), mapping.getAccessToken())) {
				throw new IllegalStateException("同一请求身份授权凭证不一致: " + identity);
			}
		}
	}

}
