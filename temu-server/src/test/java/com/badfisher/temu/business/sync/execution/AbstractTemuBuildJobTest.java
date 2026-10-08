package com.badfisher.temu.business.sync.execution;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractTemuBuildJobTest {

	@Test
	void firstPullShouldStartAt2024NewYearInShanghai() {
		assertEquals(Instant.parse("2023-12-31T16:00:00Z"), AbstractTemuBuildJob.firstPullBeginTime().toInstant());
	}

}
