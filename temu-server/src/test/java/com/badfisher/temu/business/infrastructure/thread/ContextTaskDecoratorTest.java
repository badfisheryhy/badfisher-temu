package com.badfisher.temu.business.infrastructure.thread;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ContextTaskDecorator 单元测试 — MDC 跨线程传递
 *
 * @author lenkor
 */
class ContextTaskDecoratorTest {

	@AfterEach
	void tearDown() {
		MDC.clear();
	}

	// ==================== MDC 传递 ====================

	@Test
	void shouldCaptureAndRestoreMdcInDecoratedTask() {
		MDC.put("traceId", "trace-abc123");
		MDC.put("spanId", "span-xyz789");
		MDC.put("requestId", "req-001");
		ContextTaskDecorator decorator = new ContextTaskDecorator();

		AtomicReference<String> capturedTrace = new AtomicReference<>();
		AtomicReference<String> capturedSpan = new AtomicReference<>();
		AtomicReference<String> capturedReq = new AtomicReference<>();

		Runnable task = decorator.decorate(() -> {
			capturedTrace.set(MDC.get("traceId"));
			capturedSpan.set(MDC.get("spanId"));
			capturedReq.set(MDC.get("requestId"));
		});

		MDC.clear();
		task.run();

		assertEquals("trace-abc123", capturedTrace.get());
		assertEquals("span-xyz789", capturedSpan.get());
		assertEquals("req-001", capturedReq.get());
	}

	@Test
	void shouldClearMdcAfterTaskExecution() {
		MDC.put("traceId", "trace-to-clear");
		ContextTaskDecorator decorator = new ContextTaskDecorator();

		Runnable task = decorator.decorate(() -> {
		});

		MDC.clear();
		task.run();

		// finally 块清除了 MDC — 防止线程复用时污染
		assertNull(MDC.get("traceId"));
	}

	@Test
	void shouldClearMdcEvenOnException() {
		MDC.put("traceId", "trace-ex");
		ContextTaskDecorator decorator = new ContextTaskDecorator();

		Runnable task = decorator.decorate(() -> {
			throw new RuntimeException("boom");
		});

		MDC.clear();
		try {
			task.run();
			fail("Should have thrown");
		}
		catch (RuntimeException ignored) {
		}

		assertNull(MDC.get("traceId"));
	}

	@Test
	void shouldHandleNullMdcSnapshot() {
		MDC.clear();
		ContextTaskDecorator decorator = new ContextTaskDecorator();

		AtomicReference<String> captured = new AtomicReference<>();
		Runnable task = decorator.decorate(() -> {
			captured.set(MDC.get("traceId"));
		});

		task.run();

		assertNull(captured.get());
		assertNull(MDC.get("traceId"));
	}

	@Test
	void shouldIsolateContextsBetweenSuccessiveTasks() {
		// 同一工作线程执行多个任务，上下不应串扰
		ContextTaskDecorator decorator = new ContextTaskDecorator();

		// 第一个任务
		MDC.put("traceId", "trace-1");
		Runnable task1 = decorator.decorate(() -> {
			assertEquals("trace-1", MDC.get("traceId"));
		});
		MDC.clear();
		task1.run();

		// 验证 task1 执行后上下文已清理
		assertNull(MDC.get("traceId"));

		// 第二个任务（上下文完全不同）
		MDC.put("traceId", "trace-2");
		Runnable task2 = decorator.decorate(() -> {
			assertEquals("trace-2", MDC.get("traceId"));
		});
		MDC.clear();
		task2.run();

		// task2 的上下文不应包含 task1 的残留
		assertNull(MDC.get("traceId"));
	}

	// ==================== 跨线程 ====================

	@Test
	void shouldWorkAcrossThreads() throws Exception {
		MDC.put("traceId", "trace-cross");

		ContextTaskDecorator decorator = new ContextTaskDecorator();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		CountDownLatch latch = new CountDownLatch(1);

		AtomicReference<String> threadTraceId = new AtomicReference<>();

		Runnable decoratedTask = decorator.decorate(() -> {
			threadTraceId.set(MDC.get("traceId"));
			latch.countDown();
		});

		executor.submit(decoratedTask);
		latch.await();

		assertEquals("trace-cross", threadTraceId.get());

		// 验证工作线程执行完毕后上下文已清理
		executor.submit(() -> {
			assertNull(MDC.get("traceId"));
		}).get();

		executor.shutdown();
	}

}
