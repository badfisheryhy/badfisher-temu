package com.badfisher.temu.scheduler.xxljob.amount;

import com.badfisher.temu.business.sync.execution.AbstractTemuBuildJob;
import com.badfisher.temu.business.sync.enums.TemuPullType;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.badfisher.temu.scheduler.xxljob.TemuXxlJobResult.run;

/**
 * Temu 订单金额拉取任务构建器 — 按站点为每个启用店铺创建金额拉取任务
 *
 * @author lenkor
 */
@Slf4j
@Component
@Deprecated
public class TemuOrderAmountBuildJob extends AbstractTemuBuildJob {

	@Override
	protected TemuPullType pullType() {
		return TemuPullType.ORDER_AMOUNT;
	}

	@Override
	protected String defaultPullStatus() {
		return "0";
	}

	@XxlJob("temuUsOrderAmountBuildJob")
	public void executeUs() {
		run("Temu US订单金额拉取任务构建", () -> buildForAuthRegion(TemuAuthRegion.US));
	}

}
