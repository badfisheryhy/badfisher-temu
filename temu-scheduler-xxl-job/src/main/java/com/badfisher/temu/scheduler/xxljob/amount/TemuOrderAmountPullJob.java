package com.badfisher.temu.scheduler.xxljob.amount;

import com.badfisher.temu.business.sync.execution.AbstractTemuPullJob;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.order.service.TemuOrderAmountPullService;
import com.badfisher.temu.business.sync.enums.TemuPullType;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static com.badfisher.temu.scheduler.xxljob.TemuXxlJobResult.run;

/**
 * Temu 订单金额拉取任务执行器 — 消费 pull_type=ORDER_AMOUNT 的任务，拉取金额数据
 *
 * @author lenkor
 */
@Slf4j
@Component
@Deprecated
public class TemuOrderAmountPullJob extends AbstractTemuPullJob {

	@Resource
	private TemuOrderAmountPullService temuOrderAmountPullService;

	@Override
	protected TemuPullType pullType() {
		return TemuPullType.ORDER_AMOUNT;
	}

	@Override
	protected void doPull(TemuAccessToken account, SyncTask task, TemuAuthRegion authRegion) {
		temuOrderAmountPullService.doPullTask(account, task, authRegion);
	}

	@XxlJob("temuUsOrderAmountPullJob")
	public void executeUs() {
		run("Temu US订单金额拉取任务", () -> executeForAuthRegion(TemuAuthRegion.US));
	}

}
