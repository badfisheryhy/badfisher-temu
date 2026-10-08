package com.badfisher.temu.scheduler.xxljob.aftersales;

import com.badfisher.temu.business.sync.execution.AbstractTemuPullJob;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.aftersales.service.TemuAftersalesPullService;
import com.badfisher.temu.business.sync.enums.TemuPullType;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static com.badfisher.temu.scheduler.xxljob.TemuXxlJobResult.run;

/**
 * Temu 售后单拉取任务执行器 — 消费 pull_type=AFTERSALES 的任务，拉取售后数据
 *
 * @author lenkor
 */
@Slf4j
@Component
public class TemuAftersalesPullJob extends AbstractTemuPullJob {

	@Resource
	private TemuAftersalesPullService temuAftersalesPullService;

	@Override
	protected TemuPullType pullType() {
		return TemuPullType.AFTERSALES;
	}

	@Override
	protected void doPull(TemuAccessToken account, SyncTask task, TemuAuthRegion authRegion) {
		temuAftersalesPullService.doPullTask(account, task, authRegion);
	}

	@XxlJob("temuUsAftersalesPullJob")
	public void executeUs() {
		run("Temu US售后拉取任务", () -> executeForAuthRegion(TemuAuthRegion.US));
	}

	@XxlJob("temuGlobalAftersalesPullJob")
	public void executeGlobal() {
		run("Temu GLOBAL售后拉取任务", () -> executeForAuthRegion(TemuAuthRegion.GLOBAL));
	}

	@XxlJob("temuEuAftersalesPullJob")
	public void executeEu() {
		run("Temu EU售后拉取任务", () -> executeForAuthRegion(TemuAuthRegion.EU));
	}

}
