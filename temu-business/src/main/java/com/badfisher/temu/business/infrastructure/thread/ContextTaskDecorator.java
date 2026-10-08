package com.badfisher.temu.business.infrastructure.thread;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * 线程池上下文传递装饰器 — 提交任务时快照 MDC，执行时恢复。
 * <p>
 * 解决 SLF4J MDC（traceId/spanId 等）在异步线程中丢失，导致日志无法串联的问题。 所有通过配置的线程池执行的异步任务无需手动 set/clear。
 *
 * <h3>注意事项</h3>
 * <ul>
 * <li>使用 {@link java.util.concurrent.CompletableFuture#thenAcceptAsync} 等无 Executor
 * 入参方法时， 会走 ForkJoinPool.commonPool()，不会触发本装饰器，上下文将丢失。 务必显式传入线程池，如
 * {@code thenAcceptAsync(action, temuDbExecutor)}</li>
 * <li>Spring 的 {@link org.springframework.web.context.request.RequestContextHolder} 基于
 * {@link ThreadLocal}，同样无法跨线程传递。若异步任务需要 request 属性， 需在调用前手动提取并作为方法参数传入</li>
 * </ul>
 *
 * @author lenkor
 */
public class ContextTaskDecorator implements TaskDecorator {

	@Override
	public Runnable decorate(Runnable runnable) {
		// ========== 提交线程执行：快照 MDC 上下文 ==========
		Map<String, String> mdcSnapshot = MDC.getCopyOfContextMap();

		return () -> {
			// ========== 工作线程执行：恢复上下文 ==========
			if (mdcSnapshot != null) {
				MDC.setContextMap(mdcSnapshot);
			}

			try {
				runnable.run();
			}
			finally {
				// ========== 清理：防止线程复用时上下文污染 ==========
				MDC.clear();
			}
		};
	}

}
