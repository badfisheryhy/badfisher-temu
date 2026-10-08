package com.badfisher.temu.scheduler.xxljob.aftersales;

import com.badfisher.temu.business.sync.execution.AbstractTemuBuildJob;
import com.badfisher.temu.business.sync.enums.TemuPullType;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.badfisher.temu.scheduler.xxljob.TemuXxlJobResult.run;

/**
 * Temu 售后单拉取任务构建器 — 按站点为每个启用店铺创建售后拉取任务
 *
 * @author lenkor
 */
@Slf4j
@Component
public class TemuAftersalesBuildJob extends AbstractTemuBuildJob {

	@Override
	protected TemuPullType pullType() {
		return TemuPullType.AFTERSALES;
	}

	@Override
	protected String defaultPullStatus() {
		return "0";
	}

	@XxlJob("temuUsAftersalesBuildJob")
	public void executeUs() {
		run("Temu US售后拉取任务构建", () -> buildForAuthRegion(TemuAuthRegion.US));
	}

	@XxlJob("temuGlobalAftersalesBuildJob")
	public void executeGlobal() {
		run("Temu GLOBAL售后拉取任务构建", () -> buildForAuthRegion(TemuAuthRegion.GLOBAL));
	}

	@XxlJob("temuEuAftersalesBuildJob")
	public void executeEu() {
		run("Temu EU售后拉取任务构建", () -> buildForAuthRegion(TemuAuthRegion.EU));
	}

}
