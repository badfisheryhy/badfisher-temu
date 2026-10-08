package com.badfisher.temu.business.infrastructure.thread;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Temu 异步线程池配置 — 三层设计 + 兜底线程池
 *
 * <h3>线程池分层</h3>
 * <table>
 * <tr>
 * <th>池</th>
 * <th>Bean名称</th>
 * <th>core/max</th>
 * <th>队列</th>
 * <th>职责</th>
 * </tr>
 * <tr>
 * <td>调度层</td>
 * <td>temuScheduleExecutor</td>
 * <td>8/16</td>
 * <td>40</td>
 * <td>轻量编排+任务分发</td>
 * </tr>
 * <tr>
 * <td>API层</td>
 * <td>temuApiExecutor</td>
 * <td>8/16</td>
 * <td>100</td>
 * <td>HTTP 调用 Temu 开放平台 API</td>
 * </tr>
 * <tr>
 * <td>DB层</td>
 * <td>temuDbExecutor</td>
 * <td>4/8</td>
 * <td>200</td>
 * <td>thenAcceptAsync DB写入+JSON解析</td>
 * </tr>
 * <tr>
 * <td>日志层</td>
 * <td>temuLogExecutor</td>
 * <td>1/2</td>
 * <td>1000</td>
 * <td>API 日志异步写入，避免竞争核心 DB 池</td>
 * </tr>
 * <tr>
 * <td>兜底层</td>
 * <td>temuFallbackExecutor</td>
 * <td>2/4</td>
 * <td>500</td>
 * <td>降级兜底，保障非核心异步任务不丢失</td>
 * </tr>
 * </table>
 *
 * <h3>使用方式</h3> <pre>
 *   &#064;Async                          → 默认走 temuFallbackExecutor（兜底）
 *   &#064;Async("temuScheduleExecutor")    → 调度层
 *   &#064;Async("temuApiExecutor")          → API层
 *   &#064;Async("temuDbExecutor")           → DB层
 *   &#064;Async("temuFallbackExecutor")     → 兜底层（显式指定）
 * </pre>
 *
 * <h3>默认兜底说明</h3>
 * <p>
 * 通过实现 {@link org.springframework.scheduling.annotation.AsyncConfigurer#getAsyncExecutor
 * AsyncConfigurer}， 将 {@code temuFallbackExecutor} 设为 {@code @Async} 的默认执行器。 未指定线程池名称的
 * {@code @Async} 方法不会使用 Spring 默认的 {@code SimpleAsyncTaskExecutor}（每次新建线程）， 而是走有界线程池 +
 * CallerRunsPolicy 降级保护。
 *
 * <h3>兜底线程池说明</h3>
 * <p>
 * temuFallbackExecutor 作为最后防线，当其他线程池全部饱和时接管非核心任务（如日志记录、监控上报、非关键通知等）。 采用 CallerRunsPolicy
 * + 大容量队列的组合，尽可能保证任务不丢失：
 * <ul>
 * <li>核心线程 2 个，最大 4 个，避免抢占核心业务资源</li>
 * <li>500 容量队列缓冲突发流量</li>
 * <li>CallerRunsPolicy 确保队列满时不丢任务，由调用线程兜底执行</li>
 * </ul>
 *
 * @author lenkor
 */
@Slf4j
@EnableAsync(proxyTargetClass = true)
@Configuration
public class TemuAsyncConfig implements AsyncConfigurer {

	/**
	 * 调度层 轻量编排 + 多店铺并行调度，不直接做 IO。
	 */
	@Bean("temuScheduleExecutor")
	public ThreadPoolTaskExecutor temuScheduleExecutor() {
		ThreadPoolTaskExecutor executor = createBaseExecutor("temu-sched-", 8, 16, 40, 60,
				new LoggingCallerRunsPolicy("temuScheduleExecutor"));
		log.info("temuScheduleExecutor 初始化: core=8, max=16, queue=40, rejection=CallerRunsPolicy");
		return executor;
	}

	/**
	 * API 层 — 仅处理 HTTP 调用 Temu 开放平台 API。 线程数 16 允许多店铺峰值并发。
	 */
	@Bean("temuApiExecutor")
	public ThreadPoolTaskExecutor temuApiExecutor() {
		// CallerRunsPolicy: 队列满时由调用线程执行，形成背压且不丢任务
		ThreadPoolTaskExecutor executor = createBaseExecutor("temu-api-", 8, 16, 100, 120,
				new LoggingCallerRunsPolicy("temuApiExecutor"));
		log.info("temuApiExecutor 初始化: core=8, max=16, queue=100, rejection=CallerRunsPolicy");
		return executor;
	}

	/**
	 * DB 层 — thenAcceptAsync 链式回调，JSON 解析 + 批量入库。
	 */
	@Bean("temuDbExecutor")
	public ThreadPoolTaskExecutor temuDbExecutor() {
		// CallerRunsPolicy: DB 操作耗时短，作为背压机制，防止队列无限堆积
		ThreadPoolTaskExecutor executor = createBaseExecutor("temu-db-", 4, 8, 200, 60,
				new LoggingCallerRunsPolicy("temuDbExecutor"));
		log.info("temuDbExecutor 初始化: core=4, max=8, queue=200");
		return executor;
	}

	/**
	 * 任务心跳调度器。 独立于业务线程池运行，避免 API 或数据库任务拥塞导致心跳无法按时续租。
	 */
	@Bean("temuTaskHeartbeatScheduler")
	public ThreadPoolTaskScheduler temuTaskHeartbeatScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(2);
		scheduler.setThreadNamePrefix("temu-heartbeat-");
		scheduler.setRemoveOnCancelPolicy(true);
		scheduler.setWaitForTasksToCompleteOnShutdown(false);
		scheduler.setAwaitTerminationSeconds(10);
		scheduler.initialize();
		log.info("temuTaskHeartbeatScheduler 初始化: poolSize=2");
		return scheduler;
	}

	/**
	 * 兜底层 — 最后防线，处理非核心异步任务（日志、通知、监控上报等）。
	 * <p>
	 * 当核心线程池全部饱和时，非关键任务降级到此池执行，保障系统稳定性。
	 */
	@Bean("temuFallbackExecutor")
	public ThreadPoolTaskExecutor temuFallbackExecutor() {
		// CallerRunsPolicy: 作为终极降级，队列满时由调用线程兜底执行
		ThreadPoolTaskExecutor executor = createBaseExecutor("temu-fallback-", 2, 4, 500, 60,
				new LoggingCallerRunsPolicy("temuFallbackExecutor"));
		log.info("temuFallbackExecutor 初始化: core=2, max=4, queue=500 (兜底线程池)");
		return executor;
	}

	/**
	 * API 日志独立线程池 — 避免 API 日志写入 DB 竞争核心 DB 线程池资源。
	 * <p>
	 * 日志写入属于低优先级、可丢弃的辅助任务，大容量队列 + CallerRuns 保障不丢失。
	 */
	@Bean("temuLogExecutor")
	public ThreadPoolTaskExecutor temuLogExecutor() {
		ThreadPoolTaskExecutor executor = createBaseExecutor("temu-log-", 1, 2, 1000, 60,
				new LoggingCallerRunsPolicy("temuLogExecutor"));
		log.info("temuLogExecutor 初始化: core=1, max=2, queue=1000 (日志独立池)");
		return executor;
	}

	/**
	 * 指定默认的异步执行器（即 {@code @Async} 不指定 value 时的兜底线程池）。
	 * <p>
	 * 未指定线程池名称的 {@code @Async} 方法将由 {@code temuFallbackExecutor} 执行， 避免 Spring 默认的
	 * {@code SimpleAsyncTaskExecutor} 为每个任务创建新线程导致资源耗尽。
	 */
	@Override
	public Executor getAsyncExecutor() {
		log.debug("@Async 默认使用 temuFallbackExecutor 兜底执行");
		return temuFallbackExecutor();
	}

	/**
	 * 异步任务未捕获异常处理器 — 记录日志并保留调用栈。
	 */
	@Override
	public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
		return (Throwable ex, Method method, Object... params) -> log.error("@Async 方法执行异常 | method={}.{} | params={}",
				method.getDeclaringClass().getSimpleName(), method.getName(), params, ex);
	}

	// ==================== 基础配置模板 ====================

	/**
	 * 创建线程池执行器基础配置。
	 * <p>
	 * <b>注意:</b> {@link RejectedExecutionHandler} 必须在 {@code initialize()} 之前设置， 否则
	 * Spring 5.x 的 {@code ThreadPoolTaskExecutor} 不会将其传播到底层 {@link ThreadPoolExecutor}。
	 */
	private ThreadPoolTaskExecutor createBaseExecutor(String threadPrefix, int corePoolSize, int maxPoolSize,
			int queueCapacity, int keepAliveSeconds, RejectedExecutionHandler rejectionHandler) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(corePoolSize);
		executor.setMaxPoolSize(maxPoolSize);
		executor.setQueueCapacity(queueCapacity);
		executor.setKeepAliveSeconds(keepAliveSeconds);
		executor.setThreadNamePrefix(threadPrefix);
		executor.setTaskDecorator(new ContextTaskDecorator());
		executor.setRejectedExecutionHandler(rejectionHandler);
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(60);
		executor.initialize();
		return executor;
	}

}
