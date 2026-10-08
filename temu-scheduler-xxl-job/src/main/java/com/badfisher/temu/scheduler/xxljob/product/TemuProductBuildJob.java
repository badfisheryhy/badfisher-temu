package com.badfisher.temu.scheduler.xxljob.product;

import com.badfisher.temu.business.sync.execution.AbstractTemuBuildJob;
import com.badfisher.temu.business.sync.enums.TemuPullType;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

import static com.badfisher.temu.scheduler.xxljob.TemuXxlJobResult.run;

/** Temu 商品快照任务构建器 — 商品授权统一按 CN 任务入口维护，不按销售站点拆分。 */
@Component
public class TemuProductBuildJob extends AbstractTemuBuildJob {

	@Override
	protected TemuPullType pullType() {
		return TemuPullType.PRODUCT;
	}

	@Override
	protected String defaultPullStatus() {
		return "0";
	}

	@Override
	protected String defaultTimeType() {
		return "snapshot";
	}

	@Override
	protected boolean requiresChannelId() {
		return false;
	}

	@XxlJob("temuCnProductBuildJob")
	public void executeCn() {
		run("Temu CN商品拉取任务构建", () -> buildForAuthRegion(TemuAuthRegion.CN));
	}

}
