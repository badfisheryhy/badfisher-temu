package com.badfisher.temu.business.infrastructure.thread;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LoggingCallerRunsPolicy 单元测试 — 线程池饱和降级 & 调用方 MDC 保护
 *
 * @author lenkor
 */
class LoggingCallerRunsPolicyTest {

	@AfterEach
	void tearDown() {
		MDC.clear();
	}

	// ==================== 基本降级行为 ====================

	@Test
	void shouldRunTaskInCallerThreadOnRejection() {
		LoggingCallerRunsPolicy policy = new LoggingCallerRunsPolicy("test-pool");

		ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
				policy);

		// 占满唯一线程
		CountDownLatch blocker = new CountDownLatch(1);
		executor.execute(() -> {
			try {
				blocker.await();
			}
			catch (InterruptedException ignored) {
			}
		});

		// 第二个任务会被拒绝，CallerRuns 会在调用线程（main）执行
		AtomicBoolean executed = new AtomicBoolean(false);
		String callerThreadName = Thread.currentThread().getName();
		AtomicReference<String> execThreadName = new AtomicReference<>();

		executor.execute(() -> {
			executed.set(true);
			execThreadName.set(Thread.currentThread().getName());
		});

		assertTrue(executed.get());
		assertEquals(callerThreadName, execThreadName.get());

		blocker.countDown();
		executor.shutdown();
	}

	@Test
	void shouldPropagateExceptionFromRejectedTask() {
		LoggingCallerRunsPolicy policy = new LoggingCallerRunsPolicy("test-pool");

		ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
				policy);

		// 占满线程
		CountDownLatch blocker = new CountDownLatch(1);
		executor.execute(() -> {
			try {
				blocker.await();
			}
			catch (InterruptedException ignored) {
			}
		});

		assertThrows(RuntimeException.class, () -> executor.execute(() -> {
			throw new RuntimeException("rejected-task-error");
		}));

		blocker.countDown();
		executor.shutdown();
	}

	// ==================== 调用方 MDC 保护（关键修复验证） ====================

	@Test
	void shouldRestoreCallerMdcContextAfterCallerRuns() {
		// 调用方设置了 traceId，CallerRuns 后不应丢失
		MDC.put("traceId", "caller-trace-001");
		MDC.put("spanId", "caller-span-002");

		LoggingCallerRunsPolicy policy = new LoggingCallerRunsPolicy("mdc-test-pool");
		ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
				policy);

		CountDownLatch blocker = new CountDownLatch(1);
		executor.execute(() -> {
			try {
				blocker.await();
			}
			catch (InterruptedException ignored) {
			}
		});

		executor.execute(() -> {
		});

		// 验证调用方 MDC 未被清除
		assertEquals("caller-trace-001", MDC.get("traceId"));
		assertEquals("caller-span-002", MDC.get("spanId"));

		blocker.countDown();
		executor.shutdown();
	}

	@Test
	void shouldHandleCallerRunsWhenCallerContextIsNull() {
		// 边界场景：调用方无上下文，CallerRuns 不应 NPE
		MDC.clear();

		LoggingCallerRunsPolicy policy = new LoggingCallerRunsPolicy("null-test-pool");
		ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
				policy);

		CountDownLatch blocker = new CountDownLatch(1);
		executor.execute(() -> {
			try {
				blocker.await();
			}
			catch (InterruptedException ignored) {
			}
		});

		// 不应抛异常
		executor.execute(() -> {
		});

		assertNull(MDC.get("traceId"));

		blocker.countDown();
		executor.shutdown();
	}

	@Test
	void shouldNotLeakTaskContextToCallerAfterCallerRuns() {
		// 即使任务内部修改了上下文，调用方应恢复到原始值
		MDC.put("traceId", "original-trace");

		LoggingCallerRunsPolicy policy = new LoggingCallerRunsPolicy("leak-test-pool");
		ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
				policy);

		CountDownLatch blocker = new CountDownLatch(1);
		executor.execute(() -> {
			try {
				blocker.await();
			}
			catch (InterruptedException ignored) {
			}
		});

		// 任务试图修改上下文
		executor.execute(() -> {
			MDC.put("traceId", "malicious-trace");
		});

		// 调用方上下文应恢复为原始值，不被任务修改
		assertEquals("original-trace", MDC.get("traceId"));

		blocker.countDown();
		executor.shutdown();
	}

	// ==================== 构造验证 ====================

	@Test
	void shouldConstructWithPoolName() {
		LoggingCallerRunsPolicy policy = new LoggingCallerRunsPolicy("my-custom-pool");
		assertNotNull(policy);
	}

}
