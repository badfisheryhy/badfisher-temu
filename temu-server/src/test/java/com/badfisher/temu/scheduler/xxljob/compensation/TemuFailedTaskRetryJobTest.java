package com.badfisher.temu.scheduler.xxljob.compensation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemuFailedTaskRetryJobTest {

	@Test
	void shouldParseAndDeduplicateTaskIds() {
		assertEquals(Arrays.asList(123L, 124L), TemuFailedTaskRetryJob.parseTaskIds("123, 124,123"));
	}

	@Test
	void shouldRejectBlankOrInvalidTaskIds() {
		assertThrows(IllegalArgumentException.class, () -> TemuFailedTaskRetryJob.parseTaskIds(""));
		assertThrows(IllegalArgumentException.class, () -> TemuFailedTaskRetryJob.parseTaskIds("123,error"));
		assertThrows(IllegalArgumentException.class, () -> TemuFailedTaskRetryJob.parseTaskIds("0"));
	}

}
