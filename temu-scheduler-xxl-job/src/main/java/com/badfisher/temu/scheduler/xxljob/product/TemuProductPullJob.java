package com.badfisher.temu.scheduler.xxljob.product;

import com.badfisher.temu.business.sync.execution.AbstractTemuPullJob;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.product.service.TemuProductPullService;
import com.badfisher.temu.business.sync.enums.TemuPullType;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static com.badfisher.temu.scheduler.xxljob.TemuXxlJobResult.run;

/** Temu 商品快照任务执行器 — 消费 pull_type=PRODUCT 的 CN 商品任务。 */
@Component
public class TemuProductPullJob extends AbstractTemuPullJob {

	@Resource
	private TemuProductPullService temuProductPullService;

	@Override
	protected TemuPullType pullType() {
		return TemuPullType.PRODUCT;
	}

	@Override
	protected void doPull(TemuAccessToken account, SyncTask task, TemuAuthRegion authRegion) {
		temuProductPullService.doPullTask(account, task, authRegion);
	}

	@Override
	protected boolean requiresChannelId() {
		return false;
	}

	@XxlJob("temuCnProductPullJob")
	public void executeCn() {
		run("Temu CN商品拉取任务", () -> executeForAuthRegion(TemuAuthRegion.CN));
	}

}
