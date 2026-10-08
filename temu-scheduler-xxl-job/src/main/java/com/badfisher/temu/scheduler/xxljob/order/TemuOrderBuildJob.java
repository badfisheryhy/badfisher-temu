package com.badfisher.temu.scheduler.xxljob.order;

import com.badfisher.temu.business.sync.execution.AbstractTemuBuildJob;
import com.badfisher.temu.business.sync.enums.TemuPullType;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.badfisher.temu.business.order.enums.TemuOrderStatus;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.badfisher.temu.scheduler.xxljob.TemuXxlJobResult.run;

/**
 * Temu 订单拉取任务构建器 — 按站点为每个启用店铺创建订单拉取任务
 *
 * @author lenkor
 */
@Slf4j
@Component
public class TemuOrderBuildJob extends AbstractTemuBuildJob {

	@Override
	protected TemuPullType pullType() {
		return TemuPullType.ORDER;
	}

	@Override
	protected String defaultPullStatus() {
		return String.valueOf(TemuOrderStatus.ALL.getCode());
	}

	@XxlJob("temuUsOrderBuildJob")
	public void executeUs() {
		run("Temu US订单拉取任务构建", () -> buildForAuthRegion(TemuAuthRegion.US));
	}

	@XxlJob("temuGlobalOrderBuildJob")
	public void executeGlobal() {
		run("Temu GLOBAL订单拉取任务构建", () -> buildForAuthRegion(TemuAuthRegion.GLOBAL));
	}

	@XxlJob("temuEuOrderBuildJob")
	public void executeEu() {
		run("Temu EU订单拉取任务构建", () -> buildForAuthRegion(TemuAuthRegion.EU));
	}

}
