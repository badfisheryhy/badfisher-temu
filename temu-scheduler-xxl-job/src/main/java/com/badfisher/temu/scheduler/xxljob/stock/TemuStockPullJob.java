package com.badfisher.temu.scheduler.xxljob.stock;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.business.inventory.service.TemuStockPullService;
import com.badfisher.temu.business.sync.enums.TemuPullType;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static com.badfisher.temu.scheduler.xxljob.TemuXxlJobResult.run;

/**
 * Temu CN商品授权库存直接拉取任务。
 * <p>
 * 不创建任务表记录，适合每30分钟或每小时执行。先按appId+sellerId去重授权， 再按App ID最多并行处理三个主体；每个主体内部SKC请求最多5并发，
 * 使共享API线程池总并发稳定在15以内。
 *
 * @author lenkor
 */
@Slf4j
@Component
public class TemuStockPullJob {

	private static final int APP_CONCURRENCY = 3;

	@Resource
	private TemuAccessTokenService temuAccessTokenService;

	@Resource
	private TemuStockPullService temuStockPullService;

	@Resource(name = "temuScheduleExecutor")
	private ThreadPoolTaskExecutor temuScheduleExecutor;

	@XxlJob("temuCnStockPullJob")
	public void executeCn() {
		run("Temu CN商品库存快照拉取任务", this::pullCnStock);
	}

	private void pullCnStock() {
		log.info("====== Temu CN商品授权库存快照拉取 开始 ======");
		List<TemuAccessToken> accounts = temuAccessTokenService.list(Wrappers.lambdaQuery(TemuAccessToken.class)
			.eq(TemuAccessToken::getAuthRegion, TemuAuthRegion.CN.getCode())
			.eq(TemuAccessToken::getType, TemuPullType.PRODUCT.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0));
		if (accounts.isEmpty()) {
			log.info("无CN授权区域商品账号，跳过库存快照拉取");
			return;
		}
		accounts = deduplicateAccounts(accounts);

		Map<String, List<TemuAccessToken>> accountsByAppId = groupByAppId(accounts);
		List<Map.Entry<String, List<TemuAccessToken>>> appGroups = new ArrayList<>(accountsByAppId.entrySet());
		PullSummary totalSummary = new PullSummary();

		for (int start = 0; start < appGroups.size(); start += APP_CONCURRENCY) {
			int end = Math.min(start + APP_CONCURRENCY, appGroups.size());
			List<CompletableFuture<PullSummary>> futures = new ArrayList<>(end - start);
			for (Map.Entry<String, List<TemuAccessToken>> group : appGroups.subList(start, end)) {
				futures.add(CompletableFuture.supplyAsync(() -> pullAppAccounts(group.getKey(), group.getValue()),
						temuScheduleExecutor));
			}
			for (CompletableFuture<PullSummary> future : futures) {
				totalSummary.merge(future.join());
			}
		}
		log.info("====== Temu CN商品授权库存快照拉取 完成 | 店铺 {}/{}, 库存明细 {} 条 ======", totalSummary.successAccounts,
				accounts.size(), totalSummary.stockCount);
	}

	private Map<String, List<TemuAccessToken>> groupByAppId(List<TemuAccessToken> accounts) {
		Map<String, List<TemuAccessToken>> result = new LinkedHashMap<>();
		for (TemuAccessToken account : accounts) {
			String appId = account.getAppId() == null ? "" : account.getAppId();
			result.computeIfAbsent(appId, key -> new ArrayList<>()).add(account);
		}
		return result;
	}

	private List<TemuAccessToken> deduplicateAccounts(List<TemuAccessToken> accounts) {
		Map<String, TemuAccessToken> unique = new LinkedHashMap<>();
		for (TemuAccessToken account : accounts) {
			String key = account.getAppId() + "|" + account.getSellerId() + "|" + account.getAuthRegion() + "|"
					+ account.getType();
			unique.merge(key, account, (a, b) -> {
				if (!Objects.equals(a.getAppKey(), b.getAppKey()) || !Objects.equals(a.getAppSecret(), b.getAppSecret())
						|| !Objects.equals(a.getAccessToken(), b.getAccessToken())) {
					throw new IllegalStateException("同一商品授权主体存在不一致的请求凭证: " + key);
				}
				Long aId = a.getId() == null ? Long.MAX_VALUE : a.getId();
				Long bId = b.getId() == null ? Long.MAX_VALUE : b.getId();
				return aId <= bId ? a : b;
			});
		}
		return new ArrayList<>(unique.values());
	}

	private PullSummary pullAppAccounts(String appId, List<TemuAccessToken> accounts) {
		log.info("主体 {} 库存拉取开始，店铺数={}", appId, accounts.size());
		PullSummary summary = new PullSummary();
		for (TemuAccessToken account : accounts) {
			int count = pullAccount(account);
			if (count >= 0) {
				summary.successAccounts++;
				summary.stockCount += count;
			}
		}
		log.info("主体 {} 库存拉取完成，成功店铺={}/{}, 库存明细={}", appId, summary.successAccounts, accounts.size(),
				summary.stockCount);
		return summary;
	}

	private int pullAccount(TemuAccessToken account) {
		if (account.getAppId() == null || account.getAppKey() == null || account.getAppSecret() == null
				|| account.getAccessToken() == null) {
			log.warn("店铺 {} 商品授权信息不完整，跳过库存拉取", account.getSellerId());
			return -1;
		}
		if (account.isExpired()) {
			log.warn("店铺 {} 商品accessToken已过期，跳过库存拉取", account.getSellerId());
			return -1;
		}
		try {
			return temuStockPullService.pullAccountStock(account, TemuAuthRegion.CN);
		}
		catch (Exception e) {
			log.error("店铺 {} 库存快照拉取失败，保留上一版快照", account.getSellerId(), e);
			return -1;
		}
	}

	private static final class PullSummary {

		private int successAccounts;

		private int stockCount;

		private void merge(PullSummary other) {
			this.successAccounts += other.successAccounts;
			this.stockCount += other.stockCount;
		}

	}

}
