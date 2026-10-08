package com.badfisher.temu.scheduler.xxljob.order;

import com.badfisher.temu.business.sync.execution.AbstractTemuPullJob;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.order.service.TemuOrderPullService;
import com.badfisher.temu.business.sync.enums.TemuPullType;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static com.badfisher.temu.scheduler.xxljob.TemuXxlJobResult.run;

/**
 * Temu 订单拉取任务执行器 — 消费 pull_type=ORDER 的任务，拉取订单数据
 *
 * @author lenkor
 */
@Slf4j
@Component
public class TemuOrderPullJob extends AbstractTemuPullJob {

	@Resource
	private TemuOrderPullService temuOrderPullService;

	@Override
	protected TemuPullType pullType() {
		return TemuPullType.ORDER;
	}

	@Override
	protected void doPull(TemuAccessToken account, SyncTask task, TemuAuthRegion authRegion) {
		temuOrderPullService.doPullTask(account, task, authRegion);
	}

	@XxlJob("temuUsOrderPullJob")
	public void executeUs() {
		run("Temu US订单拉取任务", () -> executeForAuthRegion(TemuAuthRegion.US));
	}

	@XxlJob("temuGlobalOrderPullJob")
	public void executeGlobal() {
		run("Temu GLOBAL订单拉取任务", () -> executeForAuthRegion(TemuAuthRegion.GLOBAL));
	}

	@XxlJob("temuEuOrderPullJob")
	public void executeEu() {
		run("Temu EU订单拉取任务", () -> executeForAuthRegion(TemuAuthRegion.EU));
	}

}
