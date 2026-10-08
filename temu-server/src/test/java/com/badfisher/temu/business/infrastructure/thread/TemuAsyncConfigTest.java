package com.badfisher.temu.business.infrastructure.thread;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TemuAsyncConfig 单元测试 — 五层线程池及任务心跳调度器配置验证。
 * <p>
 * 注意: Spring Boot 2.3.x 的 ThreadPoolTaskExecutor 没有 getQueueCapacity() /
 * getTaskDecorator()， 通过底层 ThreadPoolExecutor 和间接行为测试代替。
 *
 * @author lenkor
 */
class TemuAsyncConfigTest {

	private final TemuAsyncConfig config = new TemuAsyncConfig();

	@AfterEach
	void tearDown() {
		MDC.clear();
	}

	// ==================== 调度层 ====================

	@Test
	void shouldCreateScheduleExecutorWithCorrectPoolSize() {
		ThreadPoolTaskExecutor executor = config.temuScheduleExecutor();

		assertEquals(8, executor.getCorePoolSize());
		assertEquals(16, executor.getMaxPoolSize());
		assertEquals(60, executor.getKeepAliveSeconds());

		executor.shutdown();
	}

	@Test
	void shouldScheduleExecutorCallerRunsWhenSaturated() {
		ThreadPoolTaskExecutor executor = config.temuScheduleExecutor();
		ThreadPoolExecutor tpe = executor.getThreadPoolExecutor();

		assertTrue(tpe.getRejectedExecutionHandler() instanceof LoggingCallerRunsPolicy);

		// ThreadPoolExecutor 规则: core满→入队→队满→扩到max→还满才拒绝
		// core=8, max=16, queue=40 → 需要 8+40+8=56 个阻塞任务占满，第57个触发 CallerRuns
		CountDownLatch blocker = new CountDownLatch(1);
		int totalBlockers = 8 + 40 + 8; // core + queue + (max-core)
		for (int i = 0; i < totalBlockers; i++) {
			executor.execute(() -> {
				try {
					blocker.await();
				}
				catch (InterruptedException ignored) {
				}
			});
		}

		// 第25个任务: 队列满 + 全部线程占满 → CallerRuns 在 main 线程同步执行
		String mainThread = Thread.currentThread().getName();
		AtomicReference<String> execThread = new AtomicReference<>();
		executor.execute(() -> execThread.set(Thread.currentThread().getName()));

		assertEquals(mainThread, execThread.get());

		blocker.countDown();
		executor.shutdown();
	}

	@Test
	void shouldScheduleExecutorPropagateContext() throws Exception {
		ThreadPoolTaskExecutor executor = config.temuScheduleExecutor();

		MDC.put("traceId", "sched-trace");

		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<String> capturedTrace = new AtomicReference<>();

		executor.execute(() -> {
			capturedTrace.set(MDC.get("traceId"));
			latch.countDown();
		});

		latch.await(5, TimeUnit.SECONDS);
		assertEquals("sched-trace", capturedTrace.get());

		executor.shutdown();
	}

	// ==================== API 层 ====================

	@Test
	void shouldCreateApiExecutorWithCorrectPoolSize() {
		ThreadPoolTaskExecutor executor = config.temuApiExecutor();

		assertEquals(8, executor.getCorePoolSize());
		assertEquals(16, executor.getMaxPoolSize());
		assertEquals(120, executor.getKeepAliveSeconds());

		executor.shutdown();
	}

	@Test
	void shouldApiExecutorUseCallerRunsPolicy() {
		ThreadPoolTaskExecutor executor = config.temuApiExecutor();

		assertTrue(executor.getThreadPoolExecutor().getRejectedExecutionHandler() instanceof LoggingCallerRunsPolicy);

		executor.shutdown();
	}

	@Test
	void shouldApiExecutorCallerRunsWhenSaturated() {
		ThreadPoolTaskExecutor executor = config.temuApiExecutor();

		// 占满线程 + 队列 (max=16, queue=100)
		// ThreadPoolTaskExecutor.execute 语义: core满→入队→队满→扩到max→还满才拒绝
		CountDownLatch blocker = new CountDownLatch(1);
		for (int i = 0; i < 16 + 100; i++) {
			executor.execute(() -> {
				try {
					blocker.await();
				}
				catch (InterruptedException ignored) {
				}
			});
		}

		// 再提交由调用线程同步执行，形成背压但不丢任务
		String callerThread = Thread.currentThread().getName();
		AtomicReference<String> executionThread = new AtomicReference<>();
		executor.execute(() -> executionThread.set(Thread.currentThread().getName()));
		assertEquals(callerThread, executionThread.get());

		blocker.countDown();
		executor.shutdown();
	}

	@Test
	void shouldApiExecutorPropagateContext() throws Exception {
		ThreadPoolTaskExecutor executor = config.temuApiExecutor();

		MDC.put("traceId", "api-trace");

		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<String> capturedTrace = new AtomicReference<>();

		executor.execute(() -> {
			capturedTrace.set(MDC.get("traceId"));
			latch.countDown();
		});

		latch.await(5, TimeUnit.SECONDS);
		assertEquals("api-trace", capturedTrace.get());

		executor.shutdown();
	}

	// ==================== DB 层 ====================

	@Test
	void shouldCreateDbExecutorWithCorrectPoolSize() {
		ThreadPoolTaskExecutor executor = config.temuDbExecutor();

		assertEquals(4, executor.getCorePoolSize());
		assertEquals(8, executor.getMaxPoolSize());
		assertEquals(60, executor.getKeepAliveSeconds());

		executor.shutdown();
	}

	@Test
	void shouldDbExecutorUseCallerRunsPolicy() {
		ThreadPoolTaskExecutor executor = config.temuDbExecutor();

		assertTrue(executor.getThreadPoolExecutor().getRejectedExecutionHandler() instanceof LoggingCallerRunsPolicy);

		executor.shutdown();
	}

	@Test
	void shouldDbExecutorPropagateContext() throws Exception {
		ThreadPoolTaskExecutor executor = config.temuDbExecutor();

		MDC.put("traceId", "db-trace");

		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<String> capturedTrace = new AtomicReference<>();

		executor.execute(() -> {
			capturedTrace.set(MDC.get("traceId"));
			latch.countDown();
		});

		latch.await(5, TimeUnit.SECONDS);
		assertEquals("db-trace", capturedTrace.get());

		executor.shutdown();
	}

	@Test
	void shouldCreateIndependentHeartbeatScheduler() throws Exception {
		ThreadPoolTaskScheduler scheduler = config.temuTaskHeartbeatScheduler();

		assertEquals(2, scheduler.getScheduledThreadPoolExecutor().getCorePoolSize());
		CountDownLatch latch = new CountDownLatch(1);
		scheduler.schedule(latch::countDown, new Date());
		assertTrue(latch.await(5, TimeUnit.SECONDS));

		scheduler.shutdown();
	}

	@Test
	void shouldCreateLogExecutorWithCorrectPoolSize() {
		ThreadPoolTaskExecutor executor = config.temuLogExecutor();

		assertEquals(1, executor.getCorePoolSize());
		assertEquals(2, executor.getMaxPoolSize());
		assertTrue(executor.getThreadPoolExecutor().getRejectedExecutionHandler() instanceof LoggingCallerRunsPolicy);

		executor.shutdown();
	}

	// ==================== 兜底层 ====================

	@Test
	void shouldCreateFallbackExecutorWithCorrectPoolSize() {
		ThreadPoolTaskExecutor executor = config.temuFallbackExecutor();

		assertEquals(2, executor.getCorePoolSize());
		assertEquals(4, executor.getMaxPoolSize());
		assertEquals(60, executor.getKeepAliveSeconds());

		executor.shutdown();
	}

	@Test
	void shouldFallbackExecutorUseCallerRunsPolicy() {
		ThreadPoolTaskExecutor executor = config.temuFallbackExecutor();

		assertTrue(executor.getThreadPoolExecutor().getRejectedExecutionHandler() instanceof LoggingCallerRunsPolicy);

		executor.shutdown();
	}

	@Test
	void shouldFallbackExecutorHaveLargeQueueCapacity() {
		ThreadPoolTaskExecutor executor = config.temuFallbackExecutor();
		BlockingQueue<Runnable> queue = executor.getThreadPoolExecutor().getQueue();

		// 大容量队列 500，加上已占用的部分应至少有 300+ 的可用容量
		int remainingCapacity = queue.remainingCapacity();
		assertTrue(remainingCapacity >= 300, "兜底线程池应有大容量队列，剩余容量: " + remainingCapacity);

		// 核心线程数小，不抢占业务资源
		assertTrue(executor.getMaxPoolSize() <= 4, "兜底池不应占用过多线程");

		executor.shutdown();
	}

	@Test
	void shouldFallbackExecutorAcceptManyTasks() {
		ThreadPoolTaskExecutor executor = config.temuFallbackExecutor();

		CountDownLatch blocker = new CountDownLatch(1);
		AtomicBoolean overflowed = new AtomicBoolean(false);

		try {
			for (int i = 0; i < 300; i++) {
				executor.execute(() -> {
					try {
						blocker.await();
					}
					catch (InterruptedException ignored) {
					}
				});
			}
		}
		catch (Exception e) {
			overflowed.set(true);
		}

		assertFalse(overflowed.get(), "兜底线程池大容量队列不应被 300 个任务填满");

		blocker.countDown();
		executor.shutdown();
	}

	@Test
	void shouldFallbackExecutorPropagateContext() throws Exception {
		ThreadPoolTaskExecutor executor = config.temuFallbackExecutor();

		MDC.put("traceId", "fallback-trace");

		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<String> capturedTrace = new AtomicReference<>();

		executor.execute(() -> {
			capturedTrace.set(MDC.get("traceId"));
			latch.countDown();
		});

		latch.await(5, TimeUnit.SECONDS);
		assertEquals("fallback-trace", capturedTrace.get());

		executor.shutdown();
	}

	// ==================== 通用配置验证 ====================

	@Test
	void shouldAllExecutorsPropagateContextIndependently() throws Exception {
		// 验证所有线程池都有 ContextTaskDecorator（通过上下文传递行为间接证明）
		ThreadPoolTaskExecutor[] executors = { config.temuScheduleExecutor(), config.temuApiExecutor(),
				config.temuDbExecutor(), config.temuLogExecutor(), config.temuFallbackExecutor() };

		for (ThreadPoolTaskExecutor executor : executors) {
			MDC.put("traceId", "cross-trace");

			CountDownLatch latch = new CountDownLatch(1);
			AtomicReference<String> capturedTrace = new AtomicReference<>();

			executor.execute(() -> {
				capturedTrace.set(MDC.get("traceId"));
				latch.countDown();
			});

			latch.await(5, TimeUnit.SECONDS);
			assertEquals("cross-trace", capturedTrace.get());

			executor.shutdown();
		}
	}

	@Test
	void shouldAllExecutorsHaveUniqueThreadNamePrefix() throws Exception {
		ThreadPoolTaskExecutor sched = config.temuScheduleExecutor();
		ThreadPoolTaskExecutor api = config.temuApiExecutor();
		ThreadPoolTaskExecutor db = config.temuDbExecutor();
		ThreadPoolTaskExecutor logExecutor = config.temuLogExecutor();
		ThreadPoolTaskExecutor fallback = config.temuFallbackExecutor();

		CountDownLatch latch = new CountDownLatch(5);
		AtomicReference<String> schedName = new AtomicReference<>();
		AtomicReference<String> apiName = new AtomicReference<>();
		AtomicReference<String> dbName = new AtomicReference<>();
		AtomicReference<String> logName = new AtomicReference<>();
		AtomicReference<String> fallbackName = new AtomicReference<>();

		sched.execute(() -> {
			schedName.set(Thread.currentThread().getName());
			latch.countDown();
		});
		api.execute(() -> {
			apiName.set(Thread.currentThread().getName());
			latch.countDown();
		});
		db.execute(() -> {
			dbName.set(Thread.currentThread().getName());
			latch.countDown();
		});
		logExecutor.execute(() -> {
			logName.set(Thread.currentThread().getName());
			latch.countDown();
		});
		fallback.execute(() -> {
			fallbackName.set(Thread.currentThread().getName());
			latch.countDown();
		});

		latch.await(10, TimeUnit.SECONDS);

		assertTrue(schedName.get().contains("temu-sched-"), "调度层: " + schedName.get());
		assertTrue(apiName.get().contains("temu-api-"), "API层: " + apiName.get());
		assertTrue(dbName.get().contains("temu-db-"), "DB层: " + dbName.get());
		assertTrue(logName.get().contains("temu-log-"), "日志层: " + logName.get());
		assertTrue(fallbackName.get().contains("temu-fallback-"), "兜底层: " + fallbackName.get());

		sched.shutdown();
		api.shutdown();
		db.shutdown();
		logExecutor.shutdown();
		fallback.shutdown();
	}

	@Test
	void shouldFallbackExecutorBeIndependentFromOthers() {
		ThreadPoolTaskExecutor fallback = config.temuFallbackExecutor();
		ThreadPoolTaskExecutor sched = config.temuScheduleExecutor();

		assertNotSame(fallback, sched);
		assertNotSame(fallback.getThreadPoolExecutor(), sched.getThreadPoolExecutor());

		fallback.shutdown();
		sched.shutdown();
	}

}
