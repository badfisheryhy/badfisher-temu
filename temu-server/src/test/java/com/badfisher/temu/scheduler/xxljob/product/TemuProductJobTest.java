package com.badfisher.temu.scheduler.xxljob.product;

import com.badfisher.temu.scheduler.xxljob.product.TemuProductBuildJob;
import com.badfisher.temu.scheduler.xxljob.product.TemuProductPullJob;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class TemuProductJobTest {

	@Test
	void productBuildAndPullShouldNotRequireChannel() {
		assertFalse(new TemuProductBuildJob().requiresChannelId());
		assertFalse(new TemuProductPullJob().requiresChannelId());
	}

}
