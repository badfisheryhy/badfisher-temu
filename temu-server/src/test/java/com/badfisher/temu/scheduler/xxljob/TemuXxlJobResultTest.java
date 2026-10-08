package com.badfisher.temu.scheduler.xxljob;

import com.xxl.job.core.context.XxlJobContext;
import org.junit.jupiter.api.Test;

import static com.badfisher.temu.scheduler.xxljob.XxlJobTestSupport.executeJob;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemuXxlJobResultTest {

	@Test
	void shouldReportSuccessDescription() {
		XxlJobContext result = executeJob(null, () -> TemuXxlJobResult.run("Temu测试任务", () -> {
		}));

		assertEquals(XxlJobContext.HANDLE_CODE_SUCCESS, result.getHandleCode());
		assertEquals("Temu测试任务执行成功", result.getHandleMsg());
	}

	@Test
	void shouldReportFailureDescriptionWithoutLineBreaks() {
		XxlJobContext result = executeJob(null, () -> TemuXxlJobResult.run("Temu测试任务", () -> {
			throw new IllegalStateException("测试失败\n第二行");
		}));

		assertEquals(XxlJobContext.HANDLE_CODE_FAIL, result.getHandleCode());
		assertTrue(result.getHandleMsg().startsWith("Temu测试任务执行失败: 测试失败 第二行"));
	}

	@Test
	void shouldReportExplicitBusinessFailure() {
		XxlJobContext result = executeJob(null, () -> TemuXxlJobResult.runResult("补偿任务",
				() -> TemuXxlJobResult.failure("参数格式错误")));

		assertEquals(XxlJobContext.HANDLE_CODE_FAIL, result.getHandleCode());
		assertEquals("参数格式错误", result.getHandleMsg());
	}

	@Test
	void shouldRejectMissingBusinessResult() {
		XxlJobContext result = executeJob(null,
				() -> TemuXxlJobResult.runResult("补偿任务", () -> null));

		assertEquals(XxlJobContext.HANDLE_CODE_FAIL, result.getHandleCode());
		assertTrue(result.getHandleMsg().contains("任务未返回执行结果"));
	}

}
