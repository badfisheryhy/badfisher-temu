package com.badfisher.temu.scheduler.xxljob.amount;

import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.business.order.service.TemuOrderAmountPullService;
import com.badfisher.temu.business.order.service.TemuParentOrderService;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.badfisher.temu.business.sync.enums.TemuPullType;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

import static com.badfisher.temu.scheduler.xxljob.TemuXxlJobResult.failure;
import static com.badfisher.temu.scheduler.xxljob.TemuXxlJobResult.runResult;
import static com.badfisher.temu.scheduler.xxljob.TemuXxlJobResult.success;

/**
 * Temu 订单金额补救任务 temu已经取消半托金额数据拉取，无法通过 API 获取金额数据（后续采用rpa获取金额数据）
 * <p>
 * 核心能力：
 * <ol>
 * <li>{@link #execute()} — 批量补救：扫描近 {@value #REMEDIATE_WINDOW_DAYS} 天内创建
 * （{@code tb_temu_parent_order.create_time}）但在 {@code tb_temu_parent_order_amount}
 * 中不存在的父订单，逐单调 bg.order.amount.query 落库。</li>
 * <li>{@link #executeSingle()} — 单笔补救：按 {@code sellerId:parentOrderSn}
 * 拉取并更新金额。</li>
 * </ol>
 *
 * @author lenkor
 */
@Slf4j
@Component
@Deprecated
public class TemuOrderAmountRemediationJob {

	@Resource
	private TemuAccessTokenService temuAccessTokenService;

	@Resource
	private TemuParentOrderService temuParentOrderService;

	@Resource
	private TemuOrderAmountPullService temuOrderAmountPullService;

	/** 金额补救时间窗口（天），按父订单 create_time 过滤 */
	private static final int REMEDIATE_WINDOW_DAYS = 10;

	/**
	 * temu-api金额拉取接口已经取消 无法拉取 批量金额补救：补偿近 {@value #REMEDIATE_WINDOW_DAYS} 天创建、缺失金额记录的父订单。
	 * <p>
	 * 参数：
	 * <ul>
	 * <li><b>空</b> → 扫描所有启用店铺</li>
	 * <li><b>{@code appId|authRegion}</b> → 仅该授权主体，例如 {@code abc123|US}</li>
	 * </ul>
	 */
	@XxlJob("temuOrderAmountRemediationJob")
	public void execute() {
		String param = XxlJobHelper.getJobParam();
		runResult("Temu订单金额批量补救任务", () -> remediateBatch(param));
	}

	private ReturnT<String> remediateBatch(String param) {
		log.info("====== Temu 订单金额批量补救任务 开始, param={} ======", param);

		long sinceSeconds = System.currentTimeMillis() / 1000 - (long) REMEDIATE_WINDOW_DAYS * 24 * 60 * 60;

		List<TemuAccessToken> accounts;
		if (param == null || param.trim().isEmpty()) {
			accounts = temuAccessTokenService.lambdaQuery()
				.eq(TemuAccessToken::getAuthRegion, TemuAuthRegion.US.getCode())
				.eq(TemuAccessToken::getType, TemuPullType.ORDER_AMOUNT.getAccountType())
				.eq(TemuAccessToken::getDelFlag, 0)
				.list();
		}
		else {
			int pipeIdx = param.indexOf('|');
			if (pipeIdx <= 0 || pipeIdx >= param.length() - 1) {
				log.warn("参数格式错误，需要 appId|authRegion 格式, 实际={}", param);
				return failure("Temu订单金额批量补救任务执行失败: 参数格式错误，需要appId|authRegion格式");
			}
			String appId = param.substring(0, pipeIdx).trim();
			TemuAuthRegion authRegion = TemuAuthRegion.fromCode(param.substring(pipeIdx + 1));
			if (authRegion != TemuAuthRegion.US) {
				log.warn("订单金额补偿暂不支持授权区域 {}, 跳过", authRegion);
				return failure("Temu订单金额批量补救任务执行失败: 暂不支持授权区域" + authRegion);
			}
			accounts = temuAccessTokenService.lambdaQuery()
				.eq(TemuAccessToken::getAppId, appId)
				.eq(TemuAccessToken::getAuthRegion, authRegion.getCode())
				.eq(TemuAccessToken::getType, TemuPullType.ORDER_AMOUNT.getAccountType())
				.eq(TemuAccessToken::getDelFlag, 0)
				.list();
		}
		if (accounts.isEmpty()) {
			log.info("无启用店铺，跳过金额补救");
			return success("Temu订单金额批量补救任务执行成功: 无启用店铺，已跳过");
		}

		int totalSuccess = 0;
		int totalFailed = 0;
		for (TemuAccessToken account : accounts) {
			try {
				int[] r = remediateForAccount(account, sinceSeconds);
				totalSuccess += r[0];
				totalFailed += r[1];
			}
			catch (Exception e) {
				log.error("店铺 {} 金额补救失败", account.getSellerId(), e);
			}
		}

		log.info("====== Temu 订单金额批量补救任务 完成 | 窗口={}天, 成功={}, 失败={} ======", REMEDIATE_WINDOW_DAYS, totalSuccess,
				totalFailed);
		String resultMessage = "Temu订单金额批量补救任务执行" + (totalFailed == 0 ? "成功" : "失败") + ": 成功=" + totalSuccess + ", 失败="
				+ totalFailed;
		return totalFailed == 0 ? success(resultMessage) : failure(resultMessage);
	}

	/**
	 * 单笔金额补救：按 {@code sellerId:parentOrderSn} 拉取并更新金额。
	 * <p>
	 * 参数格式: {@code sellerId:parentOrderSn}，支持多单号逗号分隔，例如
	 * {@code 12345:24-MFRT-abc,24-MFRT-def}
	 */
	@XxlJob("temuOrderAmountSinglePullJob")
	public void executeSingle() {
		String param = XxlJobHelper.getJobParam();
		runResult("Temu订单金额单笔补救任务", () -> remediateSingle(param));
	}

	private ReturnT<String> remediateSingle(String param) {
		log.info("====== Temu 订单金额单笔补救任务 开始, param={} ======", param);

		if (param == null || param.trim().isEmpty()) {
			log.warn("参数为空，需要 sellerId:parentOrderSn 格式");
			return failure("Temu订单金额单笔补救任务执行失败: 参数为空，需要sellerId:parentOrderSn格式");
		}
		int colonIdx = param.indexOf(':');
		if (colonIdx <= 0 || colonIdx >= param.length() - 1) {
			log.warn("参数格式错误，需要 sellerId:parentOrderSn 格式, 实际={}", param);
			return failure("Temu订单金额单笔补救任务执行失败: 参数格式错误，需要sellerId:parentOrderSn格式");
		}
		String sellerId = param.substring(0, colonIdx).trim();
		String snPart = param.substring(colonIdx + 1).trim();
		if (sellerId.isEmpty() || snPart.isEmpty()) {
			log.warn("参数格式错误: sellerId 或 parentOrderSn 为空, param={}", param);
			return failure("Temu订单金额单笔补救任务执行失败: sellerId或parentOrderSn为空");
		}

		TemuAccessToken account = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getSellerId, sellerId)
			.eq(TemuAccessToken::getAuthRegion, TemuAuthRegion.US.getCode())
			.eq(TemuAccessToken::getType, TemuPullType.ORDER_AMOUNT.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.last("LIMIT 1")
			.one();
		if (account == null) {
			log.warn("店铺 {} 未找到或未启用, 跳过", sellerId);
			return failure("Temu订单金额单笔补救任务执行失败: sellerId=" + sellerId + "的店铺未找到或未启用");
		}
		TemuAuthRegion authRegion = TemuAuthRegion.fromCode(account.getAuthRegion());
		if (authRegion != TemuAuthRegion.US) {
			log.warn("订单金额补偿暂不支持授权区域 {}, 跳过", account.getAuthRegion());
			return failure("Temu订单金额单笔补救任务执行失败: 暂不支持授权区域" + account.getAuthRegion());
		}

		String[] sns = snPart.split("\\s*,\\s*");
		int success = 0;
		int failed = 0;
		for (String sn : sns) {
			String s = sn.trim();
			if (s.isEmpty())
				continue;
			if (pullSingle(s, account, authRegion)) {
				success++;
			}
			else {
				failed++;
			}
		}
		log.info("====== Temu 订单金额单笔补救任务 完成 | sellerId={}, 成功={}, 失败={} ======", sellerId, success, failed);
		String resultMessage = "Temu订单金额单笔补救任务执行" + (failed == 0 ? "成功" : "失败") + ": sellerId=" + sellerId + ", 成功="
				+ success + ", 失败=" + failed;
		return failed == 0 ? success(resultMessage) : failure(resultMessage);
	}

	/**
	 * 单店铺批量补救：从 DB 直接取缺失金额的父单号，逐单调 API 落库。
	 * @return int[]{success, failed}
	 */
	private int[] remediateForAccount(TemuAccessToken account, long sinceSeconds) {
		String sellerId = account.getSellerId();
		TemuAuthRegion authRegion = TemuAuthRegion.fromCode(account.getAuthRegion());
		if (authRegion != TemuAuthRegion.US) {
			log.warn("订单金额补偿暂不支持授权区域 {}, 跳过店铺 {}", account.getAuthRegion(), sellerId);
			return new int[] { 0, 0 };
		}
		if (!authReady(account)) {
			log.warn("店铺 {} 授权信息不完整, 跳过", sellerId);
			return new int[] { 0, 0 };
		}

		List<String> toPull = temuParentOrderService.listOrderSnsMissingAmount(sellerId, sinceSeconds);
		log.info("店铺 {} 近 {} 天待拉取金额订单数: {}", sellerId, REMEDIATE_WINDOW_DAYS, toPull.size());

		int success = 0;
		int failed = 0;
		for (String sn : toPull) {
			if (pullSingle(sn, account, authRegion)) {
				success++;
			}
			else {
				failed++;
			}
		}
		return new int[] { success, failed };
	}

	private boolean pullSingle(String parentOrderSn, TemuAccessToken account, TemuAuthRegion authRegion) {
		String sellerId = account.getSellerId();
		if (!authReady(account)) {
			log.warn("店铺 {} 授权信息不完整, 跳过订单 {}", sellerId, parentOrderSn);
			return false;
		}
		try {
			return temuOrderAmountPullService.pullAmount(parentOrderSn, account, authRegion);
		}
		catch (Exception e) {
			log.warn("店铺 {} 补救订单 {} 金额失败: {}", sellerId, parentOrderSn, e.getMessage());
			return false;
		}
	}

	private boolean authReady(TemuAccessToken account) {
		return account.getAppKey() != null && account.getAppSecret() != null && account.getAccessToken() != null;
	}

}
