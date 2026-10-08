package com.badfisher.temu.scheduler.xxljob.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.order.model.TemuOrderAddress;
import com.badfisher.temu.business.order.model.TemuParentOrder;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.business.order.service.TemuOrderAddressService;
import com.badfisher.temu.business.order.service.TemuOrderPullService;
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
import java.util.Set;
import java.util.stream.Collectors;

import static com.badfisher.temu.scheduler.xxljob.TemuXxlJobResult.failure;
import static com.badfisher.temu.scheduler.xxljob.TemuXxlJobResult.runResult;
import static com.badfisher.temu.scheduler.xxljob.TemuXxlJobResult.success;

/**
 * Temu 订单地址补救任务 — 为历史订单中缺失地址数据的记录补充收货地址。
 * <p>
 * 哪些订单会被补救：
 * <ul>
 * <li>在 parent_order 表中存在，但在 order_address 表中无对应记录</li>
 * <li>address 记录存在但 {@code mail} 字段为空（新增字段后的历史数据）</li>
 * </ul>
 *
 * @author lenkor
 */
@Slf4j
@Component
public class TemuAddressRemediationJob {

	@Resource
	private TemuAccessTokenService temuAccessTokenService;

	@Resource
	private TemuParentOrderService temuParentOrderService;

	@Resource
	private TemuOrderAddressService temuOrderAddressService;

	@Resource
	private TemuOrderPullService temuOrderPullService;

	private static final int PAGE_SIZE = 100;

	@XxlJob("temuAddressRemediationJob")
	public void execute() {
		String param = XxlJobHelper.getJobParam();
		runResult("Temu订单地址补救任务", () -> remediate(param));
	}

	private ReturnT<String> remediate(String param) {
		log.info("====== Temu 订单地址补救任务 开始, param={} ======", param);

		List<TemuAccessToken> accounts = temuAccessTokenService.lambdaQuery()
			.eq(TemuAccessToken::getType, TemuPullType.ORDER.getAccountType())
			.eq(TemuAccessToken::getDelFlag, 0)
			.list();
		if (accounts.isEmpty()) {
			log.info("无启用店铺，跳过地址补救");
			return success("Temu订单地址补救任务执行成功: 无启用店铺，已跳过");
		}

		int totalRemediated = 0;

		if (param == null || param.trim().isEmpty()) {
			for (TemuAccessToken account : accounts) {
				try {
					totalRemediated += remediateForAccount(account);
				}
				catch (Exception e) {
					log.error("店铺 {} 地址补救失败", account.getSellerId(), e);
				}
			}
		}
		else {
			int colonIdx = param.indexOf(':');
			if (colonIdx <= 0) {
				log.warn("参数格式错误，需要 channelId:parentOrderSn,... 格式, 实际={}", param);
				return failure("Temu订单地址补救任务执行失败: 参数格式错误，需要channelId:parentOrderSn,...格式");
			}
			String channelIdText = param.substring(0, colonIdx).trim();
			String snPart = param.substring(colonIdx + 1).trim();
			if (channelIdText.isEmpty() || snPart.isEmpty()) {
				log.warn("参数格式错误: channelId 或 parentOrderSn 为空, param={}", param);
				return failure("Temu订单地址补救任务执行失败: channelId或parentOrderSn为空");
			}
			Integer channelId;
			try {
				channelId = Integer.valueOf(channelIdText);
			}
			catch (NumberFormatException e) {
				log.warn("参数格式错误: channelId必须是整数, param={}", param);
				return failure("Temu订单地址补救任务执行失败: channelId必须是整数");
			}

			TemuAccessToken account = accounts.stream()
				.filter(a -> channelId.equals(a.getChannelId()))
				.findFirst()
				.orElse(null);
			if (account == null) {
				log.warn("channelId={} 的店铺未找到或未启用, 跳过", channelId);
				return failure("Temu订单地址补救任务执行失败: channelId=" + channelId + "的店铺未找到或未启用");
			}

			String[] sns = snPart.split("\\s*,\\s*");
			for (String sn : sns) {
				if (sn.trim().isEmpty())
					continue;
				boolean remediated = remediateSingleOrder(sn.trim(), account);
				if (remediated) {
					totalRemediated++;
				}
			}
		}

		log.info("====== Temu 订单地址补救任务 完成 | 共补救 {} 条 ======", totalRemediated);
		return success("Temu订单地址补救任务执行成功: 共补救" + totalRemediated + "条");
	}

	private int remediateForAccount(TemuAccessToken account) {
		String sellerId = account.getSellerId();
		if (account.getChannelId() == null) {
			log.warn("店铺 {}({}) 缺少channelId, 跳过地址补救", sellerId, account.getSite());
			return 0;
		}
		TemuAuthRegion authRegion = TemuAuthRegion.fromCode(account.getAuthRegion());
		if (authRegion == null) {
			log.warn("店铺 {} 授权区域 {} 无效, 跳过", sellerId, account.getAuthRegion());
			return 0;
		}

		if (account.getAppKey() == null || account.getAppSecret() == null || account.getAccessToken() == null) {
			log.warn("店铺 {} 授权信息不完整, 跳过", sellerId);
			return 0;
		}

		List<TemuOrderAddress> existAddresses = temuOrderAddressService
			.list(Wrappers.lambdaQuery(TemuOrderAddress.class)
				.eq(TemuOrderAddress::getSellerId, sellerId)
				.eq(TemuOrderAddress::getChannelId, account.getChannelId())
				.eq(TemuOrderAddress::getSite, account.getSite())
				.select(TemuOrderAddress::getParentOrderSn, TemuOrderAddress::getMail));
		Set<String> hasAddress = existAddresses.stream()
			.map(TemuOrderAddress::getParentOrderSn)
			.collect(Collectors.toSet());
		Set<String> missingMail = existAddresses.stream()
			.filter(a -> a.getMail() == null)
			.map(TemuOrderAddress::getParentOrderSn)
			.collect(Collectors.toSet());

		int remediated = 0;
		int page = 1;
		while (true) {
			List<TemuParentOrder> pageOrders = temuParentOrderService
				.page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, PAGE_SIZE),
						Wrappers.lambdaQuery(TemuParentOrder.class)
							.eq(TemuParentOrder::getSellerId, sellerId)
							.eq(TemuParentOrder::getChannelId, account.getChannelId())
							.eq(TemuParentOrder::getSite, account.getSite())
							.select(TemuParentOrder::getParentOrderSn))
				.getRecords();
			if (pageOrders.isEmpty()) {
				break;
			}

			for (TemuParentOrder order : pageOrders) {
				String sn = order.getParentOrderSn();
				boolean noAddress = !hasAddress.contains(sn);
				boolean needMail = missingMail.contains(sn);
				if (!noAddress && !needMail) {
					continue;
				}

				try {
					temuOrderPullService.remediateAddress(sn, sellerId, account, authRegion);
					remediated++;
					log.info("店铺 {} 订单 {} 地址补救成功", sellerId, sn);
				}
				catch (Exception e) {
					log.warn("店铺 {} 订单 {} 地址补救失败: {}", sellerId, sn, e.getMessage());
				}
			}

			if (pageOrders.size() < PAGE_SIZE) {
				break;
			}
			page++;
		}

		return remediated;
	}

	private boolean remediateSingleOrder(String parentOrderSn, TemuAccessToken account) {
		String sellerId = account.getSellerId();
		TemuAuthRegion authRegion = TemuAuthRegion.fromCode(account.getAuthRegion());
		if (authRegion == null)
			return false;

		if (account.getAppKey() == null || account.getAppSecret() == null || account.getAccessToken() == null) {
			log.warn("店铺 {} 授权信息不完整, 跳过订单 {}", sellerId, parentOrderSn);
			return false;
		}

		try {
			temuOrderPullService.remediateAddress(parentOrderSn, sellerId, account, authRegion);
			log.info("订单 {} 地址补救成功 (店铺 {})", parentOrderSn, sellerId);
			return true;
		}
		catch (Exception e) {
			log.warn("店铺 {} 补救订单 {} 失败: {}", sellerId, parentOrderSn, e.getMessage());
			return false;
		}
	}

}
